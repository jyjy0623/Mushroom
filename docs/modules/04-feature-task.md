# 蘑菇打卡应用 feature-task 模块详细设计

## 一、模块职责

- 每日任务的创建、编辑、删除、复制
- 重复任务配置
- 截止时间设定（支持提前完成奖励）
- 系统预设模板管理（晨读、备忘录、在校完成作业）
- 自定义模板保存与应用

---

## 二、领域实体

引用 core-domain，本模块使用的实体及说明：

- `Task`（任务）
- `TaskTemplate`（模板）
- `TaskStatus`
- `TaskTemplateType`
- `BonusCondition`
- `TemplateRewardConfig`

---

## 三、Use Cases 详细设计

```kotlin
// 1. 获取某日任务列表
class GetDailyTasksUseCase(private val repo: TaskRepository) {
    operator fun invoke(date: LocalDate): Flow<List<Task>>
}

// 2. 创建任务（支持重复规则，自动展开为多日任务）
class CreateTaskUseCase(private val repo: TaskRepository) {
    suspend operator fun invoke(task: Task): Result<Long>
    // 若 task.repeatRule != None，展开并批量插入
}

// 3. 更新任务
class UpdateTaskUseCase(private val repo: TaskRepository) {
    suspend operator fun invoke(task: Task): Result<Unit>
}

// 4. 删除任务（仅删单日 or 删除全部重复）
class DeleteTaskUseCase(private val repo: TaskRepository) {
    suspend operator fun invoke(taskId: Long, deleteMode: DeleteMode): Result<Unit>
}
enum class DeleteMode { SINGLE, ALL_RECURRING }

// 5. 复制任务到其他日期
class CopyTasksUseCase(private val repo: TaskRepository) {
    suspend operator fun invoke(sourceDate: LocalDate, targetDate: LocalDate): Result<Unit>
}

// 6. 获取任务模板
class GetTaskTemplatesUseCase(private val repo: TaskRepository) {
    fun invokeBuiltIn(): Flow<List<TaskTemplate>>
    fun invokeCustom(): Flow<List<TaskTemplate>>
}

// 7. 应用模板到指定日期
class ApplyTaskTemplateUseCase(private val repo: TaskRepository) {
    suspend operator fun invoke(templateId: Long, date: LocalDate): Result<Long>
    // 根据模板创建 Task，自动填充 deadline（模板 defaultDeadlineOffset + 当天0点）
}

// 8. 保存自定义模板
class SaveCustomTemplateUseCase(private val repo: TaskRepository) {
    suspend operator fun invoke(template: TaskTemplate): Result<Long>
}
```

---

## 四、ViewModel 设计

### 4.1 DailyTaskViewModel

```kotlin
class DailyTaskViewModel @Inject constructor(
    private val getDailyTasksUseCase: GetDailyTasksUseCase,
    private val deleteTaskUseCase: DeleteTaskUseCase,
    private val applyTemplateUseCase: ApplyTaskTemplateUseCase,
    private val eventBus: AppEventBus
) : ViewModel()

data class DailyTaskUiState(
    val date: LocalDate = LocalDate.now(),
    val tasks: List<TaskUiModel> = emptyList(),
    val completedCount: Int = 0,
    val totalCount: Int = 0,
    val isLoading: Boolean = false,
    val error: String? = null
)

sealed class DailyTaskEvent {
    data class LoadDate(val date: LocalDate) : DailyTaskEvent()
    data class DeleteTask(val taskId: Long, val mode: DeleteMode) : DailyTaskEvent()
    data class ApplyTemplate(val templateId: Long) : DailyTaskEvent()
    object NavigatePreviousDay : DailyTaskEvent()
    object NavigateNextDay : DailyTaskEvent()
}
```

### 4.2 TaskEditViewModel

```kotlin
class TaskEditViewModel @Inject constructor(
    private val createTaskUseCase: CreateTaskUseCase,
    private val updateTaskUseCase: UpdateTaskUseCase,
    private val parentGateway: ParentGateway
) : ViewModel()

data class TaskEditUiState(
    val taskId: Long? = null,           // null = 新建，非null = 编辑
    val title: String = "",
    val subject: Subject = Subject.MATH,
    val estimatedMinutes: Int = 30,
    val deadline: LocalDateTime? = null,
    val repeatRule: RepeatRule = RepeatRule.None,
    val description: String = "",
    val isSaving: Boolean = false,
    val saveSuccess: Boolean = false,
    val validationErrors: Map<String, String> = emptyMap()
)
```

### 4.3 TaskTemplateViewModel

```kotlin
class TaskTemplateViewModel @Inject constructor(
    private val getTaskTemplatesUseCase: GetTaskTemplatesUseCase,
    private val saveCustomTemplateUseCase: SaveCustomTemplateUseCase
) : ViewModel()

data class TaskTemplateUiState(
    val builtInTemplates: List<TaskTemplate> = emptyList(),
    val customTemplates: List<TaskTemplate> = emptyList(),
    val isLoading: Boolean = false
)
```

---

## 五、UI 页面设计

### 5.1 DailyTaskListScreen

- 顶部：日期导航（左右箭头切换日期）+ 当日完成进度（3/5 任务已完成）
- 中部：任务卡片列表
  - 每张卡片显示：任务名、学科标签、截止时间（若有）、状态
  - 提前完成的任务显示 ⚡ 标识和提前时长
  - 未完成任务显示"打卡"按钮（触发 feature-checkin 的 CheckIn 流程）
- 底部：FAB 按钮（新建任务 / 从模板添加）

### 5.2 TaskEditScreen

- 表单字段：任务名称（必填）、学科分类（下拉）、预计时长（滑动条）、截止时间（可选，时间选择器）、重复规则（单选）、备注
- 截止时间字段：开关控制显示，设定后显示具体时间，并说明"设置后可获得提前完成额外蘑菇奖励"
- 家长权限：需通过 ParentGateway 验证

### 5.3 TaskTemplateScreen

- 分 Tab：系统预设 / 我的模板
- 系统预设卡片展示三个内置模板（晨读/备忘录/在校完成作业），显示奖励规则说明
- 点击模板卡片 → 选择应用日期 → 应用

---

## 六、包结构

```
feature-task/src/main/java/com/mushroom/feature/task/
├── ui/
│   ├── DailyTaskListScreen.kt
│   ├── TaskEditScreen.kt
│   └── TaskTemplateScreen.kt
├── viewmodel/
│   ├── DailyTaskViewModel.kt
│   ├── TaskEditViewModel.kt
│   └── TaskTemplateViewModel.kt
├── model/
│   └── TaskUiModel.kt         # Domain Task → UI 展示模型的转换
└── di/
    └── TaskModule.kt          # Hilt Module
```

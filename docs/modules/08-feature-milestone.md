# feature-milestone 模块详细设计

蘑菇打卡应用 feature-milestone 模块详细设计

---

## 一、模块职责

- 里程碑配置管理（5类：过程小测/周自测/校测/期中/期末）
- 成绩录入（家长权限）
- 按分数段配置自动发放蘑菇（通过 AppEventBus 触发）
- 成绩历史与趋势

---

## 二、里程碑类型说明

| 类型 | 枚举值 | 说明 | 典型奖励等级 |
|-----|-------|------|------------|
| 过程小测验 | MINI_TEST | 日常课堂小测 | 中蘑菇 |
| 每周自测 | WEEKLY_TEST | 家长/学生自测 | 大蘑菇 |
| 学校测验 | SCHOOL_EXAM | 学校组织的单元测验 | 大蘑菇/金蘑菇 |
| 期中大考 | MIDTERM | 学期中段考试 | 金蘑菇/传说蘑菇 |
| 期末大考 | FINAL | 学期末考试 | 金蘑菇/传说蘑菇 |

---

## 三、数学试点默认分数段配置

系统为数学学科内置默认分数段配置，可在里程碑创建时一键应用：

**MINI_TEST 默认规则：**
- 90-100分 → 中蘑菇×2
- 80-89分 → 中蘑菇×1
- 60-79分 → 小蘑菇×1
- <60分 → 无奖励

**SCHOOL_EXAM 默认规则：**
- 90-100分 → 金蘑菇×5
- 80-89分 → 金蘑菇×3
- 60-79分 → 大蘑菇×1
- <60分 → 无奖励

**MIDTERM/FINAL 默认规则：**
- 90-100分 → 传说蘑菇×1（直接解锁5块拼图！）
- 80-89分 → 金蘑菇×8
- 60-79分 → 大蘑菇×2
- <60分 → 无奖励

---

## 四、Use Cases 详细设计

```kotlin
// 创建里程碑
class CreateMilestoneUseCase(
    private val repo: MilestoneRepository,
    private val parentGateway: ParentGateway
) {
    suspend operator fun invoke(milestone: Milestone): Result<Long>
}

// 录入成绩并触发奖励
class RecordMilestoneScoreUseCase(
    private val repo: MilestoneRepository,
    private val eventBus: AppEventBus,
    private val parentGateway: ParentGateway
) {
    suspend operator fun invoke(milestoneId: Long, score: Int): Result<Milestone>
    // 1. 验证家长权限
    // 2. 保存成绩（recording 后不可修改，需家长PIN删除）
    // 3. 更新 status = SCORED
    // 4. 发布 AppEvent.MilestoneScored → feature-mushroom 处理奖励
    // 5. 发布后更新 status = REWARDED
}

// 获取里程碑列表
class GetMilestonesUseCase(private val repo: MilestoneRepository) {
    fun all(): Flow<List<Milestone>>
    fun bySubject(subject: Subject): Flow<List<Milestone>>
}

// 获取里程碑成绩历史（用于统计图表）
class GetMilestoneScoreHistoryUseCase(private val repo: MilestoneRepository) {
    operator fun invoke(subject: Subject, type: MilestoneType? = null): Flow<List<MilestoneScorePoint>>
}
data class MilestoneScorePoint(
    val date: LocalDate,
    val score: Int,
    val type: MilestoneType,
    val name: String
)
```

---

## 五、ViewModel 设计

### 5.1 MilestoneListViewModel

```kotlin
data class MilestoneListUiState(
    val upcomingMilestones: List<Milestone> = emptyList(),  // 未来的里程碑
    val completedMilestones: List<Milestone> = emptyList(), // 已录入成绩
    val selectedSubject: Subject? = null,                   // 按学科筛选
    val isLoading: Boolean = false
)
```

### 5.2 MilestoneEditViewModel

```kotlin
data class MilestoneEditUiState(
    val name: String = "",
    val type: MilestoneType = MilestoneType.MINI_TEST,
    val subject: Subject = Subject.MATH,
    val scheduledDate: LocalDate = LocalDate.now(),
    val scoringRules: List<ScoringRule> = emptyList(),
    val isUsingDefaultRules: Boolean = true,
    val isSaving: Boolean = false,
    val saveSuccess: Boolean = false
)
```

---

## 六、UI 页面设计

### 6.1 MilestoneListScreen

- 分 Tab：即将到来 / 已完成
- 里程碑卡片：显示名称、学科、类型、日期
  - 已完成：显示成绩、获得的蘑菇奖励
  - 未来：显示倒计时（还有N天）
- FAB：新建里程碑
- 顶部学科筛选横向滚动栏

### 6.2 MilestoneEditScreen（创建/查看）

- 基本信息：名称、学科、类型、预计日期
- 分数段配置：默认使用预设规则（可一键应用），支持自定义每档奖励
- 录入成绩区域（已设置scheduledDate且未录入时显示）：输入框+提交（家长PIN）
- 成绩录入后显示实际分数和获得的奖励，不可再编辑

---

## 七、包结构

```
feature-milestone/src/main/java/com/mushroom/feature/milestone/
├── ui/
│   ├── MilestoneListScreen.kt
│   └── MilestoneEditScreen.kt
├── viewmodel/
│   ├── MilestoneListViewModel.kt
│   └── MilestoneEditViewModel.kt
├── usecase/
└── di/
    └── MilestoneModule.kt
```

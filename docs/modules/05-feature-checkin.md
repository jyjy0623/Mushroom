# feature-checkin 模块详细设计

蘑菇打卡应用 feature-checkin 模块详细设计

---

## 一、模块职责

- 执行单项任务打卡（记录完成时间、判断是否提前完成）
- 维护打卡历史记录（日历视图）
- 计算并维护连续打卡天数（streak）
- 打卡完成后发布 TaskCheckedIn 事件，由 feature-mushroom 订阅处理奖励

---

## 二、关键业务流程

### 打卡主流程

```
用户点击"打卡"按钮（taskId）
    │
    ▼
CheckInTaskUseCase
    ├── 1. 查询任务的 deadline
    ├── 2. 记录 checkedAt = LocalDateTime.now()
    ├── 3. 计算 isEarly = checkedAt < deadline（若 deadline 非空）
    ├── 4. 计算 earlyMinutes = deadline.minutesUntil(checkedAt)（若提前）
    ├── 5. 更新 Task.status = EARLY_DONE 或 ON_TIME_DONE
    ├── 6. 写入 CheckIn 记录到数据库
    └── 7. 发布 AppEvent.TaskCheckedIn 到 AppEventBus
    │
    ▼
feature-mushroom 订阅 TaskCheckedIn → 计算并发放蘑菇
```

### 提前完成判断规则

| 条件 | 状态 | earlyMinutes |
|------|------|--------------|
| deadline 为 null | ON_TIME_DONE | 不计算 |
| checkedAt < deadline | EARLY_DONE | > 0 |
| checkedAt >= deadline | ON_TIME_DONE | 0 |

---

## 三、Use Cases 详细设计

```kotlin
// 1. 任务打卡（核心用例）
class CheckInTaskUseCase(
    private val taskRepository: TaskRepository,
    private val checkInRepository: CheckInRepository,
    private val eventBus: AppEventBus
) {
    suspend operator fun invoke(taskId: Long, note: String? = null, imageUris: List<String> = emptyList()): Result<CheckIn>
}

// 2. 获取打卡历史（按日期范围）
class GetCheckInHistoryUseCase(private val repo: CheckInRepository) {
    operator fun invoke(start: LocalDate, end: LocalDate): Flow<Map<LocalDate, DayCheckInSummary>>
}

data class DayCheckInSummary(
    val date: LocalDate,
    val completedCount: Int,
    val totalCount: Int,
    val hasEarlyCompletion: Boolean   // 当天是否有提前完成
) {
    val completionRate: Float get() = completedCount.toFloat() / totalCount.coerceAtLeast(1)
}

// 3. 获取连续打卡天数
class GetStreakUseCase(private val repo: CheckInRepository) {
    fun currentStreak(): Flow<Int>
    fun longestStreak(): Flow<Int>
}

// 4. 检查全勤（当日所有任务是否全部完成）
class CheckAllTasksDoneUseCase(
    private val taskRepository: TaskRepository,
    private val checkInRepository: CheckInRepository,
    private val eventBus: AppEventBus
) {
    suspend operator fun invoke(date: LocalDate): Boolean
    // 若全部完成，额外发布 AppEvent 触发全勤奖励（由 feature-mushroom 处理）
}
```

---

## 四、ViewModel 设计

### 4.1 CheckInCalendarViewModel

```kotlin
class CheckInCalendarViewModel @Inject constructor(
    private val getCheckInHistoryUseCase: GetCheckInHistoryUseCase,
    private val getStreakUseCase: GetStreakUseCase
) : ViewModel()

data class CheckInCalendarUiState(
    val currentMonth: YearMonth = YearMonth.now(),
    val monthSummary: Map<LocalDate, DayCheckInSummary> = emptyMap(),
    val currentStreak: Int = 0,
    val longestStreak: Int = 0,
    val selectedDate: LocalDate? = null,
    val selectedDayDetail: List<CheckIn> = emptyList(),
    val isLoading: Boolean = false
)
```

---

## 五、UI 页面设计

### 5.1 CheckInCalendarScreen

- 顶部：当前连续打卡天数（streak badge）+ 最长记录
- 中部：月份日历视图
  - 每个日期格：根据完成率显示不同深度的绿色（0%=灰、50%=浅绿、100%=深绿）
  - 有提前完成记录的日期额外显示 ⚡ 小图标
  - 点击某天 → 展开该日详情抽屉
- 底部：月份切换按钮

### 5.2 日期详情抽屉（BottomSheet）

- 显示该日所有打卡记录
- 每条记录：任务名、完成时间、是否提前（提前多少分钟）、备注

---

## 六、包结构

```
feature-checkin/src/main/java/com/mushroom/feature/checkin/
├── ui/
│   └── CheckInCalendarScreen.kt
├── viewmodel/
│   └── CheckInCalendarViewModel.kt
├── usecase/
│   ├── CheckInTaskUseCase.kt
│   ├── GetCheckInHistoryUseCase.kt
│   ├── GetStreakUseCase.kt
│   └── CheckAllTasksDoneUseCase.kt
└── di/
    └── CheckInModule.kt
```

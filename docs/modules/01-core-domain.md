# core-domain 模块详细设计

蘑菇打卡应用 core-domain 模块详细设计。

该模块是整个应用的业务核心层，包含所有领域实体、Repository 接口、服务接口定义，纯 Kotlin，不依赖 Android SDK。

---

## 一、模块职责与边界

**职责**

- 定义所有共享领域实体（Entity）
- 定义所有 Repository 接口
- 定义所有服务接口
- 定义事件总线（AppEvent / AppEventBus）

**边界**

- 不包含任何实现代码
- 不依赖 Android SDK（可独立进行单元测试）

**被依赖方**

所有 feature 模块、core-data 模块均依赖本模块。

---

## 二、领域实体

### 2.1 Task（任务实体）

```kotlin
data class Task(
    val id: Long,
    val title: String,
    val subject: Subject,
    val estimatedMinutes: Int,
    val repeatRule: RepeatRule,
    val date: LocalDate,
    val deadline: LocalDateTime?,
    val templateType: TaskTemplateType?,
    val status: TaskStatus
)

enum class TaskStatus { PENDING, EARLY_DONE, ON_TIME_DONE, SKIPPED }

enum class TaskTemplateType { MORNING_READING, HOMEWORK_MEMO, HOMEWORK_AT_SCHOOL, CUSTOM }

enum class Subject { MATH, CHINESE, ENGLISH, PHYSICS, CHEMISTRY, BIOLOGY, HISTORY, GEOGRAPHY, OTHER }

sealed class RepeatRule {
    object None : RepeatRule()
    object Daily : RepeatRule()
    object Weekdays : RepeatRule()
    data class Custom(val daysOfWeek: Set<DayOfWeek>) : RepeatRule()
}
```

### 2.2 TaskTemplate（任务模板）

```kotlin
data class TaskTemplate(
    val id: Long,
    val name: String,
    val type: TaskTemplateType,
    val subject: Subject,
    val estimatedMinutes: Int,
    val description: String,
    val defaultDeadlineOffset: Int?,  // 距当天0点的分钟偏移
    val rewardConfig: TemplateRewardConfig,
    val isBuiltIn: Boolean
)

data class TemplateRewardConfig(
    val baseReward: MushroomRewardConfig,
    val bonusReward: MushroomRewardConfig?,
    val bonusCondition: BonusCondition?
)

sealed class BonusCondition {
    data class WithinMinutesAfterStart(val minutes: Int) : BonusCondition()
    data class ConsecutiveDays(val days: Int) : BonusCondition()
    object AllItemsDone : BonusCondition()
}
```

### 2.3 CheckIn（打卡记录）

```kotlin
data class CheckIn(
    val id: Long,
    val taskId: Long,
    val date: LocalDate,
    val checkedAt: LocalDateTime,
    val isEarly: Boolean,             // 是否提前完成
    val earlyMinutes: Int,            // 提前分钟数（0表示未提前）
    val note: String?,
    val imageUris: List<String>
)
```

### 2.4 Mushroom 相关

```kotlin
data class MushroomTransaction(
    val id: Long,
    val level: MushroomLevel,
    val action: MushroomAction,       // EARN / DEDUCT / SPEND
    val amount: Int,
    val sourceType: MushroomSource,
    val sourceId: Long?,
    val note: String?,
    val createdAt: LocalDateTime
)

enum class MushroomLevel { SMALL, MEDIUM, LARGE, GOLD, LEGEND }

enum class MushroomAction { EARN, DEDUCT, SPEND }

enum class MushroomSource {
    TASK, EARLY_BONUS, TEMPLATE_BONUS, CHECKIN_STREAK,
    MILESTONE, KEY_DATE, DEDUCTION, EXCHANGE, APPEAL_REFUND
}

data class MushroomRewardConfig(
    val level: MushroomLevel,
    val amount: Int
)

data class MushroomBalance(
    val balances: Map<MushroomLevel, Int>
) {
    fun totalPoints(): Int  // 按各等级兑换价值计算总点数
    fun get(level: MushroomLevel): Int
}
```

### 2.5 DeductionConfig 和 DeductionRecord

```kotlin
data class DeductionConfig(
    val id: Long,
    val name: String,
    val mushroomLevel: MushroomLevel,
    val defaultAmount: Int,
    val customAmount: Int,
    val isEnabled: Boolean,
    val isBuiltIn: Boolean,
    val maxPerDay: Int
)

data class DeductionRecord(
    val id: Long,
    val configId: Long,
    val mushroomLevel: MushroomLevel,
    val amount: Int,
    val reason: String,
    val recordedAt: LocalDateTime,
    val appealStatus: AppealStatus,
    val appealNote: String?
)

enum class AppealStatus { NONE, PENDING, APPROVED, REJECTED }
```

### 2.6 Reward 相关

```kotlin
data class Reward(
    val id: Long,
    val name: String,
    val imageUri: String,
    val type: RewardType,
    val requiredMushrooms: Map<MushroomLevel, Int>,
    val puzzlePieces: Int,
    val timeLimitConfig: TimeLimitConfig?,
    val status: RewardStatus
)

enum class RewardType { PHYSICAL, TIME_BASED }

enum class RewardStatus { ACTIVE, COMPLETED, CLAIMED, ARCHIVED }

data class TimeLimitConfig(
    val unitMinutes: Int,
    val periodType: PeriodType,
    val maxMinutesPerPeriod: Int,
    val cooldownDays: Int,
    val requireParentConfirm: Boolean
)

enum class PeriodType { WEEKLY, MONTHLY }

data class PuzzleProgress(
    val rewardId: Long,
    val totalPieces: Int,
    val unlockedPieces: Int
) {
    val percentage: Float get() = unlockedPieces.toFloat() / totalPieces
    val isCompleted: Boolean get() = unlockedPieces >= totalPieces
}

data class TimeRewardBalance(
    val rewardId: Long,
    val periodStart: LocalDate,
    val maxMinutes: Int,
    val usedMinutes: Int
) {
    val remainingMinutes: Int get() = maxOf(0, maxMinutes - usedMinutes)
}

data class RewardExchange(
    val id: Long,
    val rewardId: Long,
    val mushroomLevel: MushroomLevel,
    val mushroomCount: Int,
    val puzzlePiecesUnlocked: Int,
    val minutesGained: Int?,
    val createdAt: LocalDateTime
)
```

### 2.7 Milestone 相关

```kotlin
data class Milestone(
    val id: Long,
    val name: String,
    val type: MilestoneType,
    val subject: Subject,
    val scheduledDate: LocalDate,
    val scoringRules: List<ScoringRule>,
    val actualScore: Int?,
    val status: MilestoneStatus
)

enum class MilestoneType { MINI_TEST, WEEKLY_TEST, SCHOOL_EXAM, MIDTERM, FINAL }

enum class MilestoneStatus { PENDING, SCORED, REWARDED }

data class ScoringRule(
    val minScore: Int,
    val maxScore: Int,
    val rewardConfig: MushroomRewardConfig
)
```

### 2.8 KeyDate（关键日期）

```kotlin
data class KeyDate(
    val id: Long,
    val name: String,
    val date: LocalDate,
    val condition: KeyDateCondition,
    val rewardConfig: MushroomRewardConfig
)

sealed class KeyDateCondition {
    data class ConsecutiveCheckinDays(val days: Int) : KeyDateCondition()
    data class MilestoneScore(val milestoneId: Long, val minScore: Int) : KeyDateCondition()
    object ManualTrigger : KeyDateCondition()
}
```

### 2.9 AppEvent（全局事件总线）

```kotlin
sealed class AppEvent {
    data class TaskCheckedIn(
        val taskId: Long,
        val checkInTime: LocalDateTime,
        val isEarly: Boolean,
        val earlyMinutes: Int
    ) : AppEvent()

    data class MilestoneScored(
        val milestoneId: Long,
        val score: Int
    ) : AppEvent()

    data class MushroomEarned(
        val transactions: List<MushroomTransaction>
    ) : AppEvent()

    data class MushroomDeducted(
        val record: DeductionRecord
    ) : AppEvent()

    data class RewardPuzzleUpdated(
        val rewardId: Long,
        val newProgress: PuzzleProgress
    ) : AppEvent()

    data class KeyDateReached(
        val keyDateId: Long
    ) : AppEvent()
}

interface AppEventBus {
    val events: SharedFlow<AppEvent>
    suspend fun emit(event: AppEvent)
}
```

---

## 三、Repository 接口

每个 Repository 接口仅定义契约，不含任何实现。返回类型优先使用 `Flow<T>` 以支持响应式 UI 更新，写操作返回 `suspend` 函数。

### 3.1 TaskRepository

```kotlin
interface TaskRepository {
    /** 查询指定日期的所有任务，按 status/subject 排序，实时更新 */
    fun getTasksByDate(date: LocalDate): Flow<List<Task>>

    /** 查询指定日期范围内的任务（用于周视图/月视图） */
    fun getTasksByDateRange(from: LocalDate, to: LocalDate): Flow<List<Task>>

    /** 根据 ID 获取单个任务 */
    suspend fun getTaskById(id: Long): Task?

    /** 插入任务，返回新生成的 ID */
    suspend fun insertTask(task: Task): Long

    /** 更新任务（包含状态变更） */
    suspend fun updateTask(task: Task)

    /** 删除任务 */
    suspend fun deleteTask(id: Long)

    /** 根据重复规则批量生成未来任务（幂等，已存在则跳过） */
    suspend fun generateRepeatTasks(templateTaskId: Long, until: LocalDate)
}
```

### 3.2 TaskTemplateRepository

```kotlin
interface TaskTemplateRepository {
    /** 获取所有模板，内置模板排在前面 */
    fun getAllTemplates(): Flow<List<TaskTemplate>>

    /** 根据类型查询模板 */
    suspend fun getTemplateByType(type: TaskTemplateType): TaskTemplate?

    /** 插入自定义模板 */
    suspend fun insertTemplate(template: TaskTemplate): Long

    /** 更新模板（内置模板不允许修改，调用方需先校验 isBuiltIn） */
    suspend fun updateTemplate(template: TaskTemplate)

    /** 删除自定义模板 */
    suspend fun deleteTemplate(id: Long)
}
```

### 3.3 CheckInRepository

```kotlin
interface CheckInRepository {
    /** 查询指定日期的打卡记录 */
    fun getCheckInsByDate(date: LocalDate): Flow<List<CheckIn>>

    /** 查询指定日期范围内的打卡记录 */
    fun getCheckInsByDateRange(from: LocalDate, to: LocalDate): Flow<List<CheckIn>>

    /** 查询指定任务的最近一次打卡 */
    suspend fun getLatestCheckInForTask(taskId: Long): CheckIn?

    /** 插入打卡记录，返回新生成的 ID */
    suspend fun insertCheckIn(checkIn: CheckIn): Long

    /**
     * 计算截止指定日期的连续打卡天数。
     * 契约：若当天无打卡记录，则连续天数从昨天向前计算。
     */
    suspend fun getStreakCount(until: LocalDate): Int
}
```

### 3.4 MushroomRepository

```kotlin
interface MushroomRepository {
    /**
     * 获取各等级蘑菇余额（实时 Flow）。
     * 内部对 mushroom_ledger 按 level group by 聚合 EARN - DEDUCT - SPEND。
     */
    fun getBalance(): Flow<MushroomBalance>

    /** 获取完整账本流水，按 createdAt 倒序 */
    fun getLedger(limit: Int = 100): Flow<List<MushroomTransaction>>

    /** 记录一笔蘑菇流水（EARN / DEDUCT / SPEND） */
    suspend fun recordTransaction(transaction: MushroomTransaction)

    /** 批量记录蘑菇流水（原子事务） */
    suspend fun recordTransactions(transactions: List<MushroomTransaction>)
}
```

### 3.5 DeductionRepository

```kotlin
interface DeductionRepository {
    /** 获取所有扣分配置，内置配置排在前面 */
    fun getAllConfigs(): Flow<List<DeductionConfig>>

    /** 获取所有启用的扣分配置 */
    fun getEnabledConfigs(): Flow<List<DeductionConfig>>

    /** 更新扣分配置（仅允许修改 customAmount、isEnabled） */
    suspend fun updateConfig(config: DeductionConfig)

    /** 获取所有扣分记录，按 recordedAt 倒序 */
    fun getAllRecords(): Flow<List<DeductionRecord>>

    /** 插入扣分记录，返回新生成的 ID */
    suspend fun insertRecord(record: DeductionRecord): Long

    /** 更新申诉状态 */
    suspend fun updateAppealStatus(id: Long, status: AppealStatus, note: String?)

    /**
     * 查询今日指定配置已扣次数（用于检查 maxPerDay 上限）。
     * 契约：以自然日（本地时区）为边界。
     */
    suspend fun getTodayCountByConfigId(configId: Long): Int
}
```

### 3.6 RewardRepository

```kotlin
interface RewardRepository {
    /** 获取所有激活状态的奖励 */
    fun getActiveRewards(): Flow<List<Reward>>

    /** 根据 ID 获取奖励 */
    suspend fun getRewardById(id: Long): Reward?

    /** 插入奖励，返回新生成的 ID */
    suspend fun insertReward(reward: Reward): Long

    /** 更新奖励（含状态变更） */
    suspend fun updateReward(reward: Reward)

    /** 获取指定奖励的拼图进度（实时 Flow） */
    fun getPuzzleProgress(rewardId: Long): Flow<PuzzleProgress>

    /** 获取时效奖励当前周期余额 */
    suspend fun getTimeRewardBalance(rewardId: Long, periodStart: LocalDate): TimeRewardBalance?

    /** 更新时效奖励已使用分钟数 */
    suspend fun updateTimeRewardUsage(rewardId: Long, periodStart: LocalDate, usedMinutes: Int)

    /** 记录一笔奖励兑换流水 */
    suspend fun insertExchange(exchange: RewardExchange): Long
}
```

### 3.7 MilestoneRepository

```kotlin
interface MilestoneRepository {
    /** 获取所有里程碑，按 scheduledDate 倒序 */
    fun getAllMilestones(): Flow<List<Milestone>>

    /** 按科目筛选里程碑 */
    fun getMilestonesBySubject(subject: Subject): Flow<List<Milestone>>

    /** 插入里程碑，返回新生成的 ID */
    suspend fun insertMilestone(milestone: Milestone): Long

    /** 更新得分及状态 */
    suspend fun updateScore(id: Long, score: Int, status: MilestoneStatus)
}
```

### 3.8 KeyDateRepository

```kotlin
interface KeyDateRepository {
    /** 获取所有关键日期，按 date 升序 */
    fun getAllKeyDates(): Flow<List<KeyDate>>

    /** 获取即将到来的关键日期（未来 N 天内） */
    fun getUpcomingKeyDates(within: Int): Flow<List<KeyDate>>

    /** 插入关键日期，返回新生成的 ID */
    suspend fun insertKeyDate(keyDate: KeyDate): Long

    /** 删除关键日期 */
    suspend fun deleteKeyDate(id: Long)
}
```

---

## 四、服务接口

### 4.1 RewardRuleEngine

负责计算任务打卡、连续天数、里程碑等事件触发后应发放的蘑菇奖励列表。

```kotlin
interface RewardRuleEngine {
    /**
     * 根据奖励事件计算应发放的蘑菇列表。
     * 契约：本方法为纯计算，不产生任何副作用（不写库、不发事件）。
     */
    fun calculate(event: RewardEvent): List<MushroomReward>
}
```

### 4.2 DeductionRuleEngine

负责校验扣分操作的合法性（是否超过 maxPerDay 等）。

```kotlin
interface DeductionRuleEngine {
    /**
     * 校验本次扣分是否合法。
     * 返回 true 表示可以执行扣分；返回 false 表示已达上限，应阻止操作。
     */
    suspend fun canDeduct(configId: Long, date: LocalDate): Boolean
}
```

### 4.3 TaskGeneratorService

负责根据重复规则在指定日期范围内生成任务实例。

```kotlin
interface TaskGeneratorService {
    /**
     * 为所有带重复规则的任务生成指定日期的任务实例。
     * 契约：幂等——同一日期重复调用不会产生重复记录。
     * 通常由每日定时任务在凌晨触发。
     */
    suspend fun generateForDate(date: LocalDate)
}
```

### 4.4 NotificationService

抽象通知推送能力，由 core-data 或 feature-notification 模块实现（基于 AlarmManager / WorkManager）。

```kotlin
interface NotificationService {
    /** 为指定任务安排截止时间提醒 */
    suspend fun scheduleDeadlineReminder(task: Task)

    /** 取消指定任务的截止时间提醒 */
    suspend fun cancelDeadlineReminder(taskId: Long)

    /** 发送立即通知（用于打卡成功、奖励发放等） */
    suspend fun sendImmediateNotification(title: String, body: String)
}
```

### 4.5 ParentGateway

抽象家长端交互能力（审批兑换、确认时效奖励使用等），支持本地审批和远程审批两种实现。

```kotlin
interface ParentGateway {
    /**
     * 请求家长审批奖励兑换。
     * 契约：本方法应在后台挂起，直到家长操作完成（批准或拒绝）后返回结果。
     * 超时由实现方自行处理，超时视为拒绝。
     */
    suspend fun requestExchangeApproval(exchange: RewardExchange): Boolean

    /**
     * 请求家长确认开始使用时效奖励。
     * 返回 true 表示家长已确认；false 表示拒绝或超时。
     */
    suspend fun requestTimeRewardConfirmation(rewardId: Long): Boolean
}
```

---

## 五、奖励规则事件类型定义

```kotlin
sealed class RewardEvent {
    data class TaskCompleted(val task: Task, val checkIn: CheckIn) : RewardEvent()
    data class AllDailyTasksDone(val date: LocalDate) : RewardEvent()
    data class StreakReached(val days: Int) : RewardEvent()
    data class MilestoneAchieved(val milestone: Milestone) : RewardEvent()
    data class KeyDateAchieved(val keyDate: KeyDate) : RewardEvent()
}

data class MushroomReward(
    val level: MushroomLevel,
    val amount: Int,
    val reason: String,
    val sourceType: MushroomSource
)
```

---

## 六、包结构

```
core-domain/src/main/java/com/mushroom/domain/
├── entity/         # 所有领域实体
│   ├── Task.kt
│   ├── TaskTemplate.kt
│   ├── CheckIn.kt
│   ├── Mushroom.kt          # MushroomTransaction / MushroomBalance / MushroomRewardConfig
│   ├── Deduction.kt         # DeductionConfig / DeductionRecord
│   ├── Reward.kt            # Reward / TimeLimitConfig / PuzzleProgress / TimeRewardBalance / RewardExchange
│   ├── Milestone.kt
│   └── KeyDate.kt
├── repository/     # Repository 接口
│   ├── TaskRepository.kt
│   ├── TaskTemplateRepository.kt
│   ├── CheckInRepository.kt
│   ├── MushroomRepository.kt
│   ├── DeductionRepository.kt
│   ├── RewardRepository.kt
│   ├── MilestoneRepository.kt
│   └── KeyDateRepository.kt
├── service/        # 外部服务接口
│   ├── RewardRuleEngine.kt
│   ├── DeductionRuleEngine.kt
│   ├── TaskGeneratorService.kt
│   ├── NotificationService.kt
│   └── ParentGateway.kt
└── event/          # AppEvent、AppEventBus、RewardEvent
    ├── AppEvent.kt
    ├── AppEventBus.kt
    └── RewardEvent.kt
```

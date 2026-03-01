# feature-mushroom 模块详细设计

蘑菇打卡应用 feature-mushroom 模块详细设计

---

## 一、模块职责

- 蘑菇奖励引擎：响应 AppEvent，计算并发放蘑菇（统一入口）
- 蘑菇账本：记录每笔收入/扣除/消耗，维护各等级余额
- 扣分机制：家长记录不良行为，扣除蘑菇，支持学生申诉
- 关键日期奖励：管理特殊日期奖励配置
- 蘑菇等级配置：支持家长自定义等级名称和兑换价值

---

## 二、奖励引擎详细设计

### 2.1 事件订阅机制

```kotlin
// MushroomRewardEngine 在模块初始化时订阅 AppEventBus
class MushroomRewardEngine @Inject constructor(
    private val eventBus: AppEventBus,
    private val ruleEngine: RewardRuleEngine,
    private val earnMushroomUseCase: EarnMushroomUseCase,
    private val coroutineScope: CoroutineScope
) {
    init {
        coroutineScope.launch {
            eventBus.events.collect { event ->
                when (event) {
                    is AppEvent.TaskCheckedIn -> handleTaskCheckedIn(event)
                    is AppEvent.MilestoneScored -> handleMilestoneScored(event)
                    is AppEvent.KeyDateReached -> handleKeyDateReached(event)
                    else -> Unit
                }
            }
        }
    }
}
```

### 2.2 奖励规则链（Rule Chain Pattern）

```kotlin
interface RewardRule {
    fun applies(event: RewardEvent): Boolean
    fun calculate(event: RewardEvent): List<MushroomReward>
}

// 规则链（按顺序执行，所有 applies=true 的规则都会计算）
class RewardRuleChain(private val rules: List<RewardRule>) : RewardRuleEngine {
    override fun calculate(event: RewardEvent): List<MushroomReward> =
        rules.filter { it.applies(event) }.flatMap { it.calculate(event) }
}
```

### 2.3 各规则实现说明

**DailyTaskCompleteRule**
- applies: `event is RewardEvent.TaskCompleted`
- calculate: 固定返回 小蘑菇×1（TASK 来源）

**EarlyCompletionRule**
- applies: `event is RewardEvent.TaskCompleted && checkIn.isEarly`
- calculate: 按 earlyMinutes 分档（<60分钟=小蘑菇×1，60~180=小蘑菇×2，>180=中蘑菇×1），分档规则从 mushroom_config 表读取

**MorningReadingRule**
- applies: `task.templateType == MORNING_READING`
- calculate: 基础小蘑菇×1，若 checkedAt 在晨读时间段开始后10分钟内，额外+小蘑菇×1

**HomeworkMemoRule**
- applies: `task.templateType == HOMEWORK_MEMO`
- calculate: 基础小蘑菇×1，查询连续打卡天数，若达到 bonusCondition.days（默认5天）则额外+中蘑菇×1

**HomeworkAtSchoolRule**
- applies: `task.templateType == HOMEWORK_AT_SCHOOL`
- calculate: 基础中蘑菇×1，若当日所有在校作业均完成则额外+中蘑菇×1

**AllTasksDoneRule**
- applies: `event is RewardEvent.AllDailyTasksDone`
- calculate: 中蘑菇×1（CHECKIN_STREAK 来源）

**StreakRule**
- applies: `event is RewardEvent.StreakReached`
- calculate: 7天=大蘑菇×1，30天=金蘑菇×1（从配置表读取）

**MilestoneScoreRule**
- applies: `event is RewardEvent.MilestoneAchieved`
- calculate: 遍历 milestone.scoringRules，找到匹配分数段，返回对应奖励

**KeyDateRule**
- applies: `event is RewardEvent.KeyDateAchieved`
- calculate: 返回 keyDate.rewardConfig 配置的奖励

---

## 三、扣分机制详细设计

### 3.1 扣分流程

```
家长操作 → DeductMushroomUseCase
    ├── 1. 查询今日该扣分项已记录次数 (DeductionRepository.getTodayCount)
    ├── 2. 若 count >= config.maxPerDay → 返回 Result.failure("今日已达上限")
    ├── 3. 查询当前余额 (MushroomRepository.getBalance)
    ├── 4. 计算实际扣除量 = min(config.customAmount, 当前该等级余额)
    ├── 5. 写入 DeductionRecord（appealStatus = NONE）
    └── 6. 写入 mushroom_ledger（action = DEDUCT）
```

### 3.2 申诉流程

```
学生提交申诉 → AppealDeductionUseCase
    └── 更新 deduction_records.appeal_status = PENDING
          │
          ▼（家长收到通知）
    家长审核 → ReviewAppealUseCase(approved: Boolean)
          ├── approved=true：
          │     写入 mushroom_ledger（action=EARN, sourceType=APPEAL_REFUND）
          │     更新 appeal_status = APPROVED
          └── approved=false：
                更新 appeal_status = REJECTED
```

---

## 四、Use Cases 完整列表

```kotlin
// 蘑菇发放（由 MushroomRewardEngine 调用，非业务层直接调用）
class EarnMushroomUseCase(private val repo: MushroomRepository) {
    suspend operator fun invoke(rewards: List<MushroomReward>, sourceId: Long? = null): Result<Unit>
}

// 消耗蘑菇（用于兑换奖品，由 feature-reward 调用）
class SpendMushroomUseCase(private val repo: MushroomRepository) {
    suspend operator fun invoke(level: MushroomLevel, amount: Int, rewardId: Long): Result<Unit>
    // 先校验余额充足，再写账本
}

// 扣减蘑菇
class DeductMushroomUseCase(
    private val mushroomRepo: MushroomRepository,
    private val deductionRepo: DeductionRepository,
    private val ruleEngine: DeductionRuleEngine
) {
    suspend operator fun invoke(configId: Long, extraNote: String? = null): Result<DeductionRecord>
}

// 学生申诉
class AppealDeductionUseCase(private val repo: DeductionRepository) {
    suspend operator fun invoke(recordId: Long, note: String): Result<Unit>
}

// 家长审核申诉
class ReviewAppealUseCase(
    private val deductionRepo: DeductionRepository,
    private val mushroomRepo: MushroomRepository
) {
    suspend operator fun invoke(recordId: Long, approved: Boolean): Result<Unit>
}

// 查询余额
class GetMushroomBalanceUseCase(private val repo: MushroomRepository) {
    operator fun invoke(): Flow<MushroomBalance>
}

// 查询账本
class GetMushroomLedgerUseCase(private val repo: MushroomRepository) {
    operator fun invoke(): Flow<List<MushroomTransaction>>
}

// 扣分配置管理
class GetDeductionConfigsUseCase(private val repo: DeductionRepository) {
    operator fun invoke(): Flow<List<DeductionConfig>>
}
class UpdateDeductionConfigUseCase(private val repo: DeductionRepository) {
    suspend operator fun invoke(config: DeductionConfig): Result<Unit>
}

// 扣分历史
class GetDeductionHistoryUseCase(private val repo: DeductionRepository) {
    operator fun invoke(): Flow<List<DeductionRecord>>
}
```

---

## 五、ViewModel 设计

### 5.1 MushroomLedgerViewModel（蘑菇账本页）

```kotlin
data class MushroomLedgerUiState(
    val balance: MushroomBalance = MushroomBalance(emptyMap()),
    val transactions: List<MushroomTransactionUiModel> = emptyList(),
    val filterType: LedgerFilterType = LedgerFilterType.ALL,
    val isLoading: Boolean = false
)
enum class LedgerFilterType { ALL, EARN, DEDUCT, SPEND }
```

### 5.2 DeductionViewModel（扣分管理，家长用）

```kotlin
data class DeductionUiState(
    val configs: List<DeductionConfig> = emptyList(),
    val records: List<DeductionRecord> = emptyList(),
    val todayCounts: Map<Long, Int> = emptyMap(),   // configId -> 今日已记录次数
    val pendingAppeals: List<DeductionRecord> = emptyList(),
    val isParentAuthenticated: Boolean = false
)
sealed class DeductionEvent {
    data class RecordDeduction(val configId: Long, val note: String?) : DeductionEvent()
    data class ReviewAppeal(val recordId: Long, val approved: Boolean) : DeductionEvent()
    data class UpdateConfig(val config: DeductionConfig) : DeductionEvent()
    object AuthenticateParent : DeductionEvent()
}
```

---

## 六、UI 页面设计

### 6.1 MushroomLedgerScreen（蘑菇账本）

- 顶部：各等级蘑菇余额展示（图标+数量）
- 筛选栏：全部 / 获得(绿) / 扣除(红) / 消耗(蓝)
- 账本列表：每条记录显示颜色、蘑菇图标、数量、来源说明、时间

### 6.2 DeductionRecordScreen（记录扣分，家长）

- PIN 验证入口
- 扣分项列表（仅显示已开启的）
- 点击扣分项 → 确认弹窗（显示扣分数量）→ 可附加备注 → 提交

### 6.3 DeductionHistoryScreen（扣分历史）

- 列表：每条记录显示扣分原因、数量、时间、申诉状态
- 待审核的申诉标红提示（家长视角）
- 学生视角：可对 NONE 状态记录发起申诉

### 6.4 DeductionConfigScreen（扣分配置，家长）

- 每个扣分项：开关 + 数量调整（+/- 按钮）
- 显示默认值，方便重置

---

## 七、包结构

```
feature-mushroom/src/main/java/com/mushroom/feature/mushroom/
├── ui/
│   ├── MushroomLedgerScreen.kt
│   ├── DeductionRecordScreen.kt
│   ├── DeductionHistoryScreen.kt
│   └── DeductionConfigScreen.kt
├── viewmodel/
│   ├── MushroomLedgerViewModel.kt
│   └── DeductionViewModel.kt
├── engine/
│   ├── RewardRuleEngine.kt       # 接口实现
│   ├── MushroomRewardEngine.kt   # 事件订阅+驱动
│   └── rules/                   # 各规则实现类
├── usecase/                     # 所有 Use Cases
└── di/
    └── MushroomModule.kt
```

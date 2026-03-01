# feature-reward 模块详细设计

蘑菇打卡应用 feature-reward 模块详细设计

---

## 一、模块职责

- 奖品管理（创建/编辑/下架，支持实物奖品和时长型奖品两种类型）
- 拼图积累系统（奖品图片切割为N块拼图，兑换蘑菇逐步解锁）
- 时长型奖品额度管理（看电视/玩游戏，含周期上限、冷却期）
- 调用 SpendMushroomUseCase 消耗蘑菇，更新拼图进度
- 奖品完成庆祝（拼图全部解锁时触发动画）

---

## 二、奖品类型设计

### 2.1 实物奖品（PHYSICAL）

- 代表：高达模型、书籍等
- 兑换逻辑：用蘑菇解锁拼图块，拼图完成即可领取
- 无频次限制

### 2.2 时长型奖品（TIME_BASED）

- 代表：看电视、玩游戏
- 兑换逻辑：每次兑换获得一定时长（unitMinutes），不使用拼图（puzzlePieces=0）
- 有周期上限（periodType=WEEKLY/MONTHLY，maxMinutesPerPeriod）
- 有冷却期（cooldownDays，两次兑换最小间隔）
- 玩游戏类需家长二次确认（requireParentConfirm=true）

---

## 三、拼图系统设计

### 3.1 图片切割算法

```kotlin
object PuzzleCutter {
    // 将 imageUri 按网格切割为 totalPieces 块（接近正方形网格）
    // 例：20块 → 4×5 网格；25块 → 5×5 网格
    fun cut(imageUri: String, totalPieces: Int): List<PuzzlePieceUri>

    // 计算网格尺寸（确保 cols × rows = totalPieces 且尽量接近正方形）
    fun calculateGrid(totalPieces: Int): Pair<Int, Int>  // cols to rows
}
```

### 3.2 蘑菇→拼图块 换算规则

- 兑换时，用户选择消耗的蘑菇类型和数量
- 每个蘑菇（任意等级）解锁 1 块拼图
- 若余额不足（如只剩 3 块未解锁但只有 2 个蘑菇），则解锁现有量
- 蘑菇消耗记录到 mushroom_ledger（action=SPEND）

### 3.3 拼图进度计算

```kotlin
data class PuzzleProgress(
    val rewardId: Long,
    val totalPieces: Int,
    val unlockedPieces: Int
) {
    val percentage: Float get() = unlockedPieces.toFloat() / totalPieces
    val isCompleted: Boolean get() = unlockedPieces >= totalPieces
    val remainingPieces: Int get() = totalPieces - unlockedPieces
}
```

---

## 四、时长型奖品额度管理

### 4.1 周期重置逻辑

- WEEKLY：每周一0点重置
- MONTHLY：每月1日0点重置
- 重置时 time_reward_usage.used_minutes 清零，period_start 更新

### 4.2 兑换前校验

```
ExchangeMushroomsUseCase（时长型奖品）
    ├── 1. 查询当前周期已使用时长
    ├── 2. 检查 used_minutes + unitMinutes <= maxMinutesPerPeriod
    │       若超出 → 返回 Result.failure("本周/月已达上限")
    ├── 3. 检查冷却期（上次兑换距今 >= cooldownDays）
    │       若冷却中 → 返回 Result.failure("冷却期中，还需N天")
    ├── 4. 若 requireParentConfirm=true → 触发 ParentGateway.authenticate()
    ├── 5. 写入 reward_exchanges（minutes_gained=unitMinutes）
    ├── 6. 更新 time_reward_usage.used_minutes += unitMinutes
    └── 7. 调用 SpendMushroomUseCase 消耗对应蘑菇
```

---

## 五、Use Cases 完整列表

```kotlin
// 获取奖品列表
class GetActiveRewardsUseCase(private val repo: RewardRepository) {
    operator fun invoke(): Flow<List<Reward>>
}

// 创建奖品（家长操作）
class CreateRewardUseCase(
    private val repo: RewardRepository,
    private val puzzleCutter: PuzzleCutter,
    private val parentGateway: ParentGateway
) {
    suspend operator fun invoke(reward: Reward): Result<Long>
    // 实物奖品：触发图片切割，保存拼图块URI
}

// 兑换蘑菇（核心用例，实物和时长型均走此入口）
class ExchangeMushroomsUseCase(
    private val rewardRepo: RewardRepository,
    private val spendMushroomUseCase: SpendMushroomUseCase,
    private val parentGateway: ParentGateway,
    private val eventBus: AppEventBus
) {
    suspend operator fun invoke(rewardId: Long, mushroomLevel: MushroomLevel, amount: Int): Result<PuzzleProgress>
    // 成功后发布 AppEvent.RewardPuzzleUpdated（若实物奖品完成则触发庆祝动画）
}

// 查询拼图进度
class GetPuzzleProgressUseCase(private val repo: RewardRepository) {
    operator fun invoke(rewardId: Long): Flow<PuzzleProgress>
}

// 查询时长型奖品余额
class GetTimeRewardBalanceUseCase(private val repo: RewardRepository) {
    operator fun invoke(rewardId: Long): Flow<TimeRewardBalance>
}

// 家长确认奖品领取
class ClaimRewardUseCase(
    private val repo: RewardRepository,
    private val parentGateway: ParentGateway
) {
    suspend operator fun invoke(rewardId: Long): Result<Unit>
}
```

---

## 六、ViewModel 设计

### 6.1 RewardListViewModel

```kotlin
data class RewardListUiState(
    val rewards: List<RewardUiModel> = emptyList(),
    val isLoading: Boolean = false
)
data class RewardUiModel(
    val reward: Reward,
    val puzzleProgress: PuzzleProgress?,           // PHYSICAL 奖品
    val timeBalance: TimeRewardBalance?            // TIME_BASED 奖品
)
```

### 6.2 RewardDetailViewModel

```kotlin
data class RewardDetailUiState(
    val reward: Reward? = null,
    val puzzleProgress: PuzzleProgress? = null,
    val timeBalance: TimeRewardBalance? = null,
    val currentBalance: MushroomBalance = MushroomBalance(emptyMap()),
    val isExchanging: Boolean = false,
    val exchangeSuccess: Boolean = false,
    val celebrationTrigger: Boolean = false,  // 拼图完成庆祝触发
    val error: String? = null
)
```

---

## 七、UI 页面设计

### 7.1 RewardListScreen

- 奖品卡片网格（2列）
- 实物奖品：显示拼图进度条 + 已解锁块数/总块数
- 时长型奖品：显示本周/本月已用时长/上限（如 "30/90 分钟"）
- 待领取的奖品显示"待领取"标签（拼图已完成）

### 7.2 RewardDetailScreen（实物奖品）

- 顶部：拼图大图展示（已解锁块显示图片，未解锁显示灰色/模糊遮罩）
- 中部：进度信息（X/20 块已解锁）
- 底部：兑换区域（选择蘑菇类型和数量 → 预览将解锁的块数 → 确认兑换）
- 拼图完成时触发全屏庆祝动画

### 7.3 RewardDetailScreen（时长型奖品）

- 顶部：奖品图片
- 中部：本周期额度展示（进度环形图：已用/上限）
- 底部：兑换按钮（显示可获得的时长）+ 冷却期倒计时（若在冷却中）

---

## 八、包结构

```
feature-reward/src/main/java/com/mushroom/feature/reward/
├── ui/
│   ├── RewardListScreen.kt
│   └── RewardDetailScreen.kt
├── viewmodel/
│   ├── RewardListViewModel.kt
│   └── RewardDetailViewModel.kt
├── puzzle/
│   └── PuzzleCutter.kt
├── usecase/
└── di/
    └── RewardModule.kt
```

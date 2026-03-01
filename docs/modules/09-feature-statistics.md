# feature-statistics 模块详细设计

蘑菇打卡应用 feature-statistics 模块详细设计

---

## 一、模块职责

- 聚合多模块数据，提供学习数据统计和趋势分析
- 学习时长趋势、打卡情况、成绩趋势、蘑菇收支统计
- 只读模块：不写入任何数据，仅从各 Repository 读取

---

## 二、统计维度

### 2.1 打卡统计

- 当前连续天数（streak）、最长连续天数
- 近30天每日完成率（热力图数据）
- 按学科分布（各科任务完成率）

### 2.2 蘑菇统计

- 当前各等级余额
- 近30天蘑菇收入趋势（折线图）
- 蘑菇来源分布（饼图/条形图：任务、提前奖励、里程碑、扣分等）
- 总获得 vs 总消耗

### 2.3 成绩统计（仅有里程碑成绩才显示）

- 按学科显示成绩趋势折线图
- 显示各里程碑类型的成绩趋势

### 2.4 奖品进度

- 各奖品当前拼图进度汇总
- 预估达成时间（基于近期平均蘑菇获取速度）

---

## 三、Use Cases（均为只读）

```kotlin
class GetCheckInStatisticsUseCase(
    private val checkInRepo: CheckInRepository,
    private val taskRepo: TaskRepository
) {
    operator fun invoke(period: StatisticsPeriod): Flow<CheckInStatistics>
}
data class CheckInStatistics(
    val currentStreak: Int,
    val longestStreak: Int,
    val totalCheckins: Int,
    val averageDailyCompletion: Float,
    val dailyCompletionRates: List<DailyRate>,       // 用于热力图
    val subjectBreakdown: Map<Subject, Float>         // 各科完成率
)

class GetMushroomStatisticsUseCase(
    private val mushroomRepo: MushroomRepository
) {
    operator fun invoke(period: StatisticsPeriod): Flow<MushroomStatistics>
}
data class MushroomStatistics(
    val currentBalance: MushroomBalance,
    val totalEarned: Map<MushroomLevel, Int>,
    val totalSpent: Map<MushroomLevel, Int>,
    val totalDeducted: Map<MushroomLevel, Int>,
    val earningTrend: List<DailyMushroomEarning>,    // 近N天每日获得量
    val sourceBreakdown: Map<MushroomSource, Int>    // 来源分布
)

class GetScoreStatisticsUseCase(
    private val milestoneRepo: MilestoneRepository
) {
    operator fun invoke(subject: Subject): Flow<ScoreStatistics>
}
data class ScoreStatistics(
    val subject: Subject,
    val scorePoints: List<MilestoneScorePoint>,
    val averageScore: Float,
    val bestScore: Int,
    val trend: ScoreTrend    // IMPROVING / STABLE / DECLINING
)
enum class ScoreTrend { IMPROVING, STABLE, DECLINING }

enum class StatisticsPeriod { LAST_7_DAYS, LAST_30_DAYS, THIS_SEMESTER }
```

---

## 四、ViewModel 设计

```kotlin
class StatisticsViewModel @Inject constructor(
    private val checkInStatsUseCase: GetCheckInStatisticsUseCase,
    private val mushroomStatsUseCase: GetMushroomStatisticsUseCase,
    private val scoreStatsUseCase: GetScoreStatisticsUseCase
) : ViewModel()

data class StatisticsUiState(
    val period: StatisticsPeriod = StatisticsPeriod.LAST_30_DAYS,
    val checkInStats: CheckInStatistics? = null,
    val mushroomStats: MushroomStatistics? = null,
    val scoreStats: Map<Subject, ScoreStatistics> = emptyMap(),
    val isLoading: Boolean = false
)
```

---

## 五、UI 页面设计

StatisticsScreen（分 Tab 或滚动页面）：

### 5.1 学习情况 Tab

- 顶部：streak 卡片（当前/最长）
- 近30天打卡热力图（GitHub contribution 风格日历）
- 各科完成率条形图

### 5.2 蘑菇收支 Tab

- 当前各等级余额（图标+数量）
- 近30天每日蘑菇收入折线图
- 来源分布条形图（任务/提前奖励/里程碑/扣分等颜色区分）

### 5.3 成绩趋势 Tab

- 学科选择横向滚动
- 成绩折线图（按时间序列，不同里程碑类型用不同颜色线）
- 平均分、最高分、趋势标签（进步中/稳定/需关注）

---

## 六、包结构

```
feature-statistics/src/main/java/com/mushroom/feature/statistics/
├── ui/
│   └── StatisticsScreen.kt
├── viewmodel/
│   └── StatisticsViewModel.kt
├── usecase/
│   ├── GetCheckInStatisticsUseCase.kt
│   ├── GetMushroomStatisticsUseCase.kt
│   └── GetScoreStatisticsUseCase.kt
└── di/
    └── StatisticsModule.kt
```

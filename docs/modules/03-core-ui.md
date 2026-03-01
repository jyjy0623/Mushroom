# 蘑菇打卡应用 core-ui 模块详细设计

## 一、模块职责

- 提供全应用共用的 Jetpack Compose UI 组件
- 统一视觉风格、主题色、字体
- 不包含业务逻辑，不依赖 core-domain

---

## 二、主题与设计规范

### 2.1 颜色体系

```kotlin
object MushroomColors {
    val Primary = Color(0xFF4CAF50)        // 蘑菇绿
    val Secondary = Color(0xFFFF9800)      // 橙色（蘑菇帽）
    val EarnGreen = Color(0xFF43A047)      // 加分绿
    val DeductRed = Color(0xFFE53935)      // 扣分红
    val SpendBlue = Color(0xFF1E88E5)      // 消耗蓝
    val GoldMushroom = Color(0xFFFFD700)   // 金蘑菇
    val LegendMushroom = Color(0xFF9C27B0) // 传说蘑菇紫
    val Background = Color(0xFFF5F5F0)     // 米白背景
    val Surface = Color(0xFFFFFFFF)
    val EarlyBadge = Color(0xFF00BCD4)     // 提前完成标识青色
}
```

### 2.2 字体与排版

使用系统默认字体，标题 20sp，正文 14sp，说明文字 12sp

### 2.3 蘑菇等级对应视觉配置

| 等级 | 图标颜色 | 图标尺寸 | 动画效果 |
|-----|---------|---------|---------|
| SMALL | 棕色 | 24dp | 无 |
| MEDIUM | 深绿 | 32dp | 轻微弹跳 |
| LARGE | 深橙 | 40dp | 弹跳 |
| GOLD | 金色 | 48dp | 旋转+弹跳 |
| LEGEND | 紫色渐变 | 56dp | 粒子爆炸 |

---

## 三、公共组件目录

### 3.1 蘑菇相关组件

```kotlin
// 蘑菇图标组件
@Composable
fun MushroomIcon(level: MushroomLevel, size: Dp, animated: Boolean = false)

// 蘑菇数量展示（带等级图标）
@Composable
fun MushroomCountBadge(level: MushroomLevel, count: Int)

// 蘑菇奖励弹窗（打卡后浮现，带动画）
@Composable
fun MushroomRewardPopup(rewards: List<MushroomReward>, onDismiss: () -> Unit)

// 蘑菇账本条目（绿色/红色/蓝色三色）
@Composable
fun MushroomLedgerItem(transaction: MushroomTransactionUiModel)
```

### 3.2 拼图组件

```kotlin
// 拼图进度展示（核心组件）
@Composable
fun PuzzleBoard(
    imageUri: String,
    totalPieces: Int,
    unlockedPieces: Int,
    onPieceUnlocked: () -> Unit   // 解锁动画回调
)

// 拼图进度条（简化版，用于列表页）
@Composable
fun PuzzleProgressBar(progress: PuzzleProgress, modifier: Modifier)

// 拼图完成庆祝动画（全屏）
@Composable
fun PuzzleCompleteAnimation(rewardName: String, onFinished: () -> Unit)
```

### 3.3 任务相关组件

```kotlin
// 任务卡片
@Composable
fun TaskCard(
    task: TaskUiModel,
    onCheckIn: (Long) -> Unit,
    onEdit: (Long) -> Unit
)

// 截止时间标签
@Composable
fun DeadlineBadge(deadline: LocalDateTime, currentTime: LocalDateTime)

// 提前完成标识
@Composable
fun EarlyCompletionBadge(earlyMinutes: Int)  // 显示 ⚡ 和提前时长
```

### 3.4 通用组件

```kotlin
// 家长权限确认对话框
@Composable
fun ParentPinDialog(onConfirm: (String) -> Unit, onDismiss: () -> Unit)

// 分段进度标题（用于每日任务完成度）
@Composable
fun DailyProgressHeader(completed: Int, total: Int)

// 空状态占位图
@Composable
fun EmptyStateView(message: String, iconRes: Int)

// 连续打卡streak显示
@Composable
fun StreakBadge(days: Int)
```

### 3.5 图表组件（包装 Vico 库）

```kotlin
// 折线图（用于成绩趋势、学习时长趋势）
@Composable
fun TrendLineChart(dataPoints: List<ChartDataPoint>, title: String)

// 柱状图（用于每周打卡情况）
@Composable
fun WeeklyBarChart(data: List<DailyCheckInData>)
```

---

## 四、动画规范

### 4.1 蘑菇收集动画

- 触发：打卡成功后
- 效果：蘑菇图标从任务卡片飞向顶部蘑菇余额区域（飞行动画）
- 时长：600ms，easeInOut

### 4.2 拼图解锁动画

- 触发：用蘑菇换拼图时
- 效果：新解锁的拼图块从灰色渐变为真实图片，带轻微缩放
- 时长：400ms/块

### 4.3 拼图完成庆祝

- 触发：拼图全部完成
- 效果：全屏烟花粒子特效 + 完整奖品图片放大展示
- 时长：3秒

### 4.4 扣分提示动画

- 触发：当日有扣分记录时进入首页
- 效果：蘑菇余额数字短暂变红色闪烁
- 时长：1秒

---

## 五、包结构

```
core-ui/src/main/java/com/mushroom/ui/
├── theme/          # 颜色、字体、主题定义
├── component/
│   ├── mushroom/   # 蘑菇相关组件
│   ├── puzzle/     # 拼图组件
│   ├── task/       # 任务相关组件
│   └── common/     # 通用组件、图表
└── animation/      # 动画定义
```

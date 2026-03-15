package com.mushroom.feature.statistics.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mushroom.core.domain.entity.CheckInStatistics
import com.mushroom.core.domain.entity.MilestoneScorePoint
import com.mushroom.core.domain.entity.MilestoneType
import com.mushroom.core.domain.entity.MushroomLevel
import com.mushroom.core.domain.entity.MushroomStatistics
import com.mushroom.core.ui.themedDisplayName
import com.mushroom.core.ui.themedEmoji
import com.mushroom.core.ui.R as CoreUiR
import com.mushroom.core.domain.entity.ScoreStatistics
import com.mushroom.core.domain.entity.ScoreTrend
import com.mushroom.core.domain.entity.StatisticsPeriod
import com.mushroom.core.domain.entity.Subject
import com.mushroom.feature.game.viewmodel.GameViewModel
import com.mushroom.feature.statistics.viewmodel.StatisticsViewModel
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatisticsScreen(
    onNavigateToMilestoneList: () -> Unit = {},
    viewModel: StatisticsViewModel = hiltViewModel(),
    gameViewModel: GameViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val topScores by gameViewModel.topScores.collectAsStateWithLifecycle()
    val globalLeaderboard by gameViewModel.globalLeaderboard.collectAsStateWithLifecycle()
    val friendLeaderboard by gameViewModel.friendLeaderboard.collectAsStateWithLifecycle()
    var tabIndex by remember { mutableStateOf(0) }

    Scaffold(
        topBar = { CenterAlignedTopAppBar(title = { Text("数据统计") }) }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // 时间段选择（跑酷 Tab 不需要，仅前三个 Tab 用到）
            if (tabIndex < 3) {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    item {
                        FilterChip(
                            selected = uiState.period == StatisticsPeriod.LAST_7_DAYS,
                            onClick = { viewModel.selectPeriod(StatisticsPeriod.LAST_7_DAYS) },
                            label = { Text("近7天") }
                        )
                    }
                    item {
                        FilterChip(
                            selected = uiState.period == StatisticsPeriod.LAST_30_DAYS,
                            onClick = { viewModel.selectPeriod(StatisticsPeriod.LAST_30_DAYS) },
                            label = { Text("近30天") }
                        )
                    }
                    item {
                        FilterChip(
                            selected = uiState.period == StatisticsPeriod.THIS_SEMESTER,
                            onClick = { viewModel.selectPeriod(StatisticsPeriod.THIS_SEMESTER) },
                            label = { Text("本学期") }
                        )
                    }
                }
            }

            // Tabs
            TabRow(selectedTabIndex = tabIndex) {
                Tab(selected = tabIndex == 0, onClick = { tabIndex = 0 }, text = { Text("学习\n情况", textAlign = androidx.compose.ui.text.style.TextAlign.Center) })
                Tab(selected = tabIndex == 1, onClick = { tabIndex = 1 }, text = { Text(stringResource(CoreUiR.string.tab_currency_flow), textAlign = androidx.compose.ui.text.style.TextAlign.Center) })
                Tab(selected = tabIndex == 2, onClick = { tabIndex = 2 }, text = { Text("成绩\n趋势", textAlign = androidx.compose.ui.text.style.TextAlign.Center) })
                Tab(selected = tabIndex == 3, onClick = { tabIndex = 3 }, text = { Text("跑酷\n游戏", textAlign = androidx.compose.ui.text.style.TextAlign.Center) })
            }

            if (uiState.isLoading && tabIndex < 3) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("加载中…")
                }
            } else {
                when (tabIndex) {
                    0 -> CheckInTab(stats = uiState.checkInStats)
                    1 -> MushroomTab(stats = uiState.mushroomStats)
                    2 -> ScoreTab(scoreStats = uiState.scoreStats, onNavigateToMilestoneList = onNavigateToMilestoneList)
                    3 -> GameLeaderboardTab(
                        scores = topScores,
                        globalState = globalLeaderboard,
                        friendState = friendLeaderboard,
                        onLoadGlobal = { gameViewModel.loadGlobalLeaderboard() },
                        onLoadFriend = { gameViewModel.loadFriendLeaderboard() }
                    )
                }
            }
        }
    }
}

// -----------------------------------------------------------------------
// Tab 1: 学习情况
// -----------------------------------------------------------------------
@Composable
private fun CheckInTab(stats: CheckInStatistics?) {
    if (stats == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("暂无打卡数据")
        }
    } else {
    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        item {
            // Streak 卡片
            Card(Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                        StreakItem("当前连续", "${stats.currentStreak} 天")
                    }
                    Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                        StreakItem("最长连续", "${stats.longestStreak} 天")
                    }
                    Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                        StreakItem("总打卡", "${stats.totalCheckins} 次")
                    }
                }
            }
        }
        item {
            // 平均完成率
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text("平均日完成率", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "${(stats.averageDailyCompletion * 100).toInt()}%",
                        style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    LinearProgressIndicator(
                        progress = { stats.averageDailyCompletion },
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                    )
                }
            }
        }
        item {
            // 学科完成率
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text("学科完成率", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(8.dp))
                    stats.subjectBreakdown
                        .filter { it.value > 0 }
                        .forEach { (subject, rate) ->
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    subjectLabel(subject),
                                    modifier = Modifier.padding(end = 8.dp),
                                    style = MaterialTheme.typography.bodySmall
                                )
                                LinearProgressIndicator(
                                    progress = { rate },
                                    modifier = Modifier.weight(1f)
                                )
                                Text(
                                    " ${(rate * 100).toInt()}%",
                                    style = MaterialTheme.typography.bodySmall,
                                    modifier = Modifier.padding(start = 8.dp)
                                )
                            }
                        }
                }
            }
        }
    }
    }
}

@Composable
private fun StreakItem(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

// -----------------------------------------------------------------------
// Tab 2: 蘑菇收支
// -----------------------------------------------------------------------
@Composable
private fun MushroomTab(stats: MushroomStatistics?) {
    if (stats == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(stringResource(CoreUiR.string.no_currency_data))
        }
    } else {
    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        item {
            // 当前余额 - 与蘑菇页余额卡片布局一致
            Card(
                Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
            ) {
                Column(Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("当前余额", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text(
                            "总积分：${stats.currentBalance.totalPoints()}",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                    Spacer(Modifier.height(12.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                        MushroomLevel.values().forEach { level ->
                            val cnt = stats.currentBalance.get(level)
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(level.themedEmoji(), fontSize = 24.sp)
                                Text(cnt.toString(), style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
                                Text(level.themedDisplayName(), style = MaterialTheme.typography.labelSmall)
                                Text(
                                    "${level.exchangePoints}积分",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.6f)
                                )
                            }
                        }
                    }
                }
            }
        }
        item {
            // 期间收支 - 按蘑菇等级分列展示
            Card(
                Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
            ) {
                Column(Modifier.padding(16.dp)) {
                    val totalEarned = stats.totalEarned.entries.sumOf { (level, cnt) -> level.exchangePoints * cnt }
                    val totalSpent = stats.totalSpent.entries.sumOf { (level, cnt) -> level.exchangePoints * cnt }
                    val totalDeducted = stats.totalDeducted.entries.sumOf { (level, cnt) -> level.exchangePoints * cnt }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("期间收支", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text(
                            "净：${totalEarned - totalSpent - totalDeducted}积分",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                    Spacer(Modifier.height(12.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                        MushroomLevel.values().forEach { level ->
                            val earned = stats.totalEarned[level] ?: 0
                            val spent = (stats.totalSpent[level] ?: 0) + (stats.totalDeducted[level] ?: 0)
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(level.themedEmoji(), fontSize = 24.sp)
                                Text(
                                    "+$earned",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.tertiary
                                )
                                Text(
                                    "-$spent",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.error
                                )
                                Text(level.themedDisplayName(), style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                }
            }
        }
    }
    }
}

// -----------------------------------------------------------------------
// Tab 3: 成绩趋势
// -----------------------------------------------------------------------
@Composable
private fun ScoreTab(scoreStats: Map<Subject, ScoreStatistics>, onNavigateToMilestoneList: () -> Unit) {
    var selectedSubject by remember { mutableStateOf(Subject.MATH) }
    val currentStats = scoreStats[selectedSubject]

    Column(Modifier.fillMaxSize()) {
        // 学科选择
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(Subject.values()) { subject ->
                FilterChip(
                    selected = selectedSubject == subject,
                    onClick = { selectedSubject = subject },
                    label = { Text(subjectLabel(subject)) }
                )
            }
        }

        if (currentStats == null || currentStats.scorePoints.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("${subjectLabel(selectedSubject)}暂无成绩记录")
                    Spacer(Modifier.height(12.dp))
                    androidx.compose.material3.OutlinedButton(onClick = onNavigateToMilestoneList) {
                        Text("前往里程碑录入成绩")
                    }
                }
            }
        } else {
        val today = LocalDate.now()
        val yearToMonthToDayToPoints = currentStats.scorePoints
            .sortedByDescending { it.date }
            .groupBy { it.date.year }
            .toSortedMap(compareByDescending { it })
            .mapValues { (_, pts) ->
                pts.groupBy { it.date.monthValue }
                    .toSortedMap(compareByDescending { it })
                    .mapValues { (_, mpts) ->
                        mpts.groupBy { it.date }
                            .toSortedMap(compareByDescending { it })
                    }
            }
        val expandedYears = rememberSaveable { mutableStateOf(setOf(today.year)) }
        val expandedMonths = rememberSaveable { mutableStateOf(setOf(today.year * 100 + today.monthValue)) }
        val expandedDates = rememberSaveable { mutableStateOf(setOf(today.toString())) }

        LazyColumn(
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        Text("成绩统计", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(8.dp))
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("${currentStats.averageScore.toInt()}", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary)
                                Text("平均分", style = MaterialTheme.typography.labelSmall)
                            }
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("${currentStats.bestScore}", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.tertiary)
                                Text("最高分", style = MaterialTheme.typography.labelSmall)
                            }
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(trendLabel(currentStats.trend), style = MaterialTheme.typography.titleLarge, color = trendColor(currentStats.trend))
                                Text("趋势", style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                }
            }
            // 折線圖
            item {
                ScoreLineChart(scorePoints = currentStats.scorePoints)
            }
            // 歷史成績：年→月→日 三級分組折叠

            yearToMonthToDayToPoints.forEach { (year, monthToDayToPoints) ->
                val isYearExpanded = year in expandedYears.value
                val totalForYear = monthToDayToPoints.values.sumOf { it.values.sumOf { d -> d.size } }
                item(key = "score_year_$year") {
                    ScoreYearHeader(
                        year = year,
                        count = totalForYear,
                        isExpanded = isYearExpanded,
                        onToggle = {
                            expandedYears.value = if (isYearExpanded)
                                expandedYears.value - year else expandedYears.value + year
                        }
                    )
                }
                if (isYearExpanded) {
                    monthToDayToPoints.forEach { (month, dayToPoints) ->
                        val monthKey = year * 100 + month
                        val isMonthExpanded = monthKey in expandedMonths.value
                        val totalForMonth = dayToPoints.values.sumOf { it.size }
                        item(key = "score_month_${year}_$month") {
                            ScoreMonthHeader(
                                year = year, month = month, count = totalForMonth,
                                isExpanded = isMonthExpanded,
                                onToggle = {
                                    expandedMonths.value = if (isMonthExpanded)
                                        expandedMonths.value - monthKey else expandedMonths.value + monthKey
                                }
                            )
                        }
                        if (isMonthExpanded) {
                            dayToPoints.forEach { (date, points) ->
                                val dateKey = date.toString()
                                val isDateExpanded = dateKey in expandedDates.value
                                item(key = "score_day_$dateKey") {
                                    ScoreDayHeader(
                                        date = date,
                                        count = points.size,
                                        isExpanded = isDateExpanded,
                                        onToggle = {
                                            expandedDates.value = if (isDateExpanded)
                                                expandedDates.value - dateKey else expandedDates.value + dateKey
                                        }
                                    )
                                }
                                if (isDateExpanded) {
                                    items(points, key = { "score_pt_${it.date}_${it.name}" }) { point ->
                                        ScorePointCard(point = point)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        }
    }
}

@Composable
private fun trendColor(trend: ScoreTrend) = when (trend) {
    ScoreTrend.IMPROVING -> MaterialTheme.colorScheme.tertiary
    ScoreTrend.STABLE    -> MaterialTheme.colorScheme.primary
    ScoreTrend.DECLINING -> MaterialTheme.colorScheme.error
}

@Composable
private fun scoreColor(score: Int) = when {
    score >= 90 -> MaterialTheme.colorScheme.tertiary
    score >= 60 -> MaterialTheme.colorScheme.primary
    else -> MaterialTheme.colorScheme.error
}

private fun trendLabel(trend: ScoreTrend) = when (trend) {
    ScoreTrend.IMPROVING -> "进步中 ↑"
    ScoreTrend.STABLE    -> "稳定"
    ScoreTrend.DECLINING -> "需关注 ↓"
}

private fun subjectLabel(subject: Subject) = when (subject) {
    Subject.MATH -> "数学"; Subject.CHINESE -> "语文"; Subject.ENGLISH -> "英语"
    Subject.PHYSICS -> "物理"; Subject.CHEMISTRY -> "化学"; Subject.BIOLOGY -> "生物"
    Subject.HISTORY -> "历史"; Subject.GEOGRAPHY -> "地理"; Subject.OTHER -> "其他"
}

private fun milestoneTypeLabel(type: MilestoneType) = when (type) {
    MilestoneType.MINI_TEST -> "小测"; MilestoneType.WEEKLY_TEST -> "周测"
    MilestoneType.SCHOOL_EXAM -> "校测"; MilestoneType.MIDTERM -> "期中"; MilestoneType.FINAL -> "期末"
}

// -----------------------------------------------------------------------
// 成绩折线图
// -----------------------------------------------------------------------
@Composable
private fun ScoreLineChart(scorePoints: List<com.mushroom.core.domain.entity.MilestoneScorePoint>) {
    // 小测验：MINI_TEST；大考：其余类型
    val quizPoints = scorePoints
        .filter { it.type == MilestoneType.MINI_TEST }
        .sortedBy { it.date }
    val examPoints = scorePoints
        .filter { it.type != MilestoneType.MINI_TEST }
        .sortedBy { it.date }

    if (quizPoints.isEmpty() && examPoints.isEmpty()) return

    val quizColor = Color(0xFF4CAF50)   // 绿色：小测验
    val examColor = Color(0xFF2196F3)   // 蓝色：大考

    val textMeasurer = rememberTextMeasurer()
    val axisTextStyle = TextStyle(fontSize = 10.sp, color = Color.Gray)

    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text("成绩趋势", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(4.dp))
            // 图例
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                if (quizPoints.isNotEmpty()) LegendItem(color = quizColor, label = "小测验")
                if (examPoints.isNotEmpty()) LegendItem(color = examColor, label = "校测/期中/期末")
            }
            Spacer(Modifier.height(8.dp))

            val allPoints = (quizPoints + examPoints)
            val minScore = (allPoints.minOf { it.score } - 5).coerceAtLeast(0)
            val maxScore = (allPoints.maxOf { it.score } + 5).coerceAtMost(100)
            val allDates = allPoints.map { it.date }.distinct().sorted()

            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(180.dp)
            ) {
                val w = size.width
                val h = size.height
                val padLeft = 40f
                val padRight = 16f
                val padTop = 12f
                val padBottom = 36f
                val chartW = w - padLeft - padRight
                val chartH = h - padTop - padBottom

                // Y 轴刻度
                val gridLines = listOf(0, 25, 50, 75, 100).filter { it in minScore..maxScore }
                gridLines.forEach { score ->
                    val yFrac = 1f - (score - minScore).toFloat() / (maxScore - minScore).coerceAtLeast(1)
                    val y = padTop + yFrac * chartH
                    drawLine(
                        color = Color.LightGray.copy(alpha = 0.5f),
                        start = Offset(padLeft, y),
                        end = Offset(padLeft + chartW, y),
                        strokeWidth = 1f
                    )
                    // Y 轴分数标注
                    val textResult = textMeasurer.measure(score.toString(), axisTextStyle)
                    drawText(
                        textLayoutResult = textResult,
                        topLeft = Offset(padLeft - textResult.size.width - 4f, y - textResult.size.height / 2f)
                    )
                }

                // X 轴日期标注（最多显示 6 个，避免重叠）
                val totalDates = allDates.size.coerceAtLeast(1)
                val dateFmt = java.time.format.DateTimeFormatter.ofPattern("M/d")
                val step = if (allDates.size <= 6) 1 else (allDates.size / 5).coerceAtLeast(1)
                allDates.forEachIndexed { idx, date ->
                    if (idx % step == 0 || idx == allDates.lastIndex) {
                        val xFrac = if (totalDates > 1) idx.toFloat() / (totalDates - 1) else 0.5f
                        val x = padLeft + xFrac * chartW
                        val label = date.format(dateFmt)
                        val textResult = textMeasurer.measure(label, axisTextStyle)
                        drawText(
                            textLayoutResult = textResult,
                            topLeft = Offset(x - textResult.size.width / 2f, padTop + chartH + 4f)
                        )
                    }
                }

                fun drawSeries(points: List<com.mushroom.core.domain.entity.MilestoneScorePoint>, color: Color) {
                    if (points.isEmpty()) return

                    val offsets = points.map { pt ->
                        val xIdx = allDates.indexOf(pt.date).toFloat()
                        val xFrac = if (totalDates > 1) xIdx / (totalDates - 1) else 0.5f
                        val yFrac = 1f - (pt.score - minScore).toFloat() / (maxScore - minScore).coerceAtLeast(1)
                        Offset(padLeft + xFrac * chartW, padTop + yFrac * chartH)
                    }

                    if (offsets.size >= 2) {
                        val path = Path().apply {
                            moveTo(offsets[0].x, offsets[0].y)
                            offsets.drop(1).forEach { lineTo(it.x, it.y) }
                        }
                        drawPath(path, color = color, style = Stroke(width = 3f, cap = StrokeCap.Round, join = StrokeJoin.Round))
                    }
                    offsets.forEach { drawCircle(color = color, radius = 5f, center = it) }
                }

                drawSeries(quizPoints, quizColor)
                drawSeries(examPoints, examColor)
            }
        }
    }
}

@Composable
private fun LegendItem(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Canvas(modifier = Modifier.width(20.dp).height(3.dp)) {
            drawLine(color = color, start = Offset(0f, size.height / 2), end = Offset(size.width, size.height / 2), strokeWidth = 4f)
        }
        Spacer(Modifier.width(4.dp))
        Text(label, style = MaterialTheme.typography.labelSmall)
    }
}

// -----------------------------------------------------------------------
// 成绩历史分组 Header / Card
// -----------------------------------------------------------------------
@Composable
private fun ScoreYearHeader(year: Int, count: Int, isExpanded: Boolean, onToggle: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onToggle)
            .padding(horizontal = 4.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = if (isExpanded) Icons.Filled.KeyboardArrowDown
                          else Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            modifier = Modifier.size(20.dp),
            tint = MaterialTheme.colorScheme.primary
        )
        Spacer(Modifier.width(4.dp))
        Text(
            "${year}年",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.weight(1f)
        )
        Text(
            "$count 条",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun ScoreMonthHeader(year: Int, month: Int, count: Int, isExpanded: Boolean, onToggle: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onToggle)
            .padding(start = 24.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = if (isExpanded) Icons.Filled.KeyboardArrowDown
                          else Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            modifier = Modifier.size(18.dp),
            tint = MaterialTheme.colorScheme.secondary
        )
        Spacer(Modifier.width(4.dp))
        Text(
            "${month}月",
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.secondary,
            modifier = Modifier.weight(1f)
        )
        Text(
            "$count 条",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun ScoreDayHeader(date: LocalDate, count: Int, isExpanded: Boolean, onToggle: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onToggle)
            .padding(start = 44.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = if (isExpanded) Icons.Filled.KeyboardArrowDown
                          else Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            modifier = Modifier.size(16.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.width(4.dp))
        Text(
            "${date.monthValue}月${date.dayOfMonth}日",
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.weight(1f)
        )
        Text(
            "$count 条",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun ScorePointCard(point: MilestoneScorePoint) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 56.dp)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(point.name, style = MaterialTheme.typography.bodyMedium)
                Text(
                    "${point.date} · ${milestoneTypeLabel(point.type)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(
                "${point.score}分",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = scoreColor(point.score)
            )
        }
    }
}

// -----------------------------------------------------------------------
// Tab 4: 跑酷游戏排行榜
// -----------------------------------------------------------------------
private val GAME_DATE_FMT = DateTimeFormatter.ofPattern("MM-dd HH:mm")

@Composable
private fun GameLeaderboardTab(
    scores: List<com.mushroom.feature.game.entity.GameScore>,
    globalState: com.mushroom.feature.game.viewmodel.GlobalLeaderboardState,
    friendState: com.mushroom.feature.game.viewmodel.GlobalLeaderboardState,
    onLoadGlobal: () -> Unit,
    onLoadFriend: () -> Unit
) {
    var subTab by remember { mutableStateOf(0) }

    LaunchedEffect(Unit) {
        onLoadGlobal()
    }

    Column(modifier = Modifier.fillMaxSize()) {
        TabRow(selectedTabIndex = subTab) {
            Tab(selected = subTab == 0, onClick = { subTab = 0 }, text = { Text("本地记录") })
            Tab(
                selected = subTab == 1,
                onClick = {
                    subTab = 1
                    onLoadGlobal()
                },
                text = { Text("全球排行") }
            )
            Tab(
                selected = subTab == 2,
                onClick = {
                    subTab = 2
                    onLoadFriend()
                },
                text = { Text("好友排行") }
            )
        }

        when (subTab) {
            0 -> LocalGameScoresContent(scores)
            1 -> com.mushroom.feature.game.ui.GlobalLeaderboardTab(
                state = globalState,
                onRetry = onLoadGlobal
            )
            2 -> com.mushroom.feature.game.ui.GlobalLeaderboardTab(
                state = friendState,
                onRetry = onLoadFriend
            )
        }
    }
}

@Composable
private fun LocalGameScoresContent(scores: List<com.mushroom.feature.game.entity.GameScore>) {
    if (scores.isEmpty()) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("还没有游戏记录", style = MaterialTheme.typography.bodyLarge)
                Spacer(Modifier.height(8.dp))
                Text(
                    "完成所有任务后解锁游戏！",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    } else {
        LazyColumn(
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            itemsIndexed(scores) { index, score ->
                val rank = index + 1
                val rankLabel = when (rank) { 1 -> "🥇"; 2 -> "🥈"; 3 -> "🥉"; else -> "$rank." }
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = if (rank <= 3)
                            MaterialTheme.colorScheme.primaryContainer
                        else MaterialTheme.colorScheme.surface
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = rankLabel,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.width(40.dp)
                        )
                        Text(
                            text = "${score.score} 分",
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            text = score.playedAt.format(GAME_DATE_FMT),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

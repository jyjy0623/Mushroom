package com.mushroom.feature.statistics.ui

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mushroom.core.domain.entity.CheckInStatistics
import com.mushroom.core.domain.entity.MilestoneType
import com.mushroom.core.domain.entity.MushroomLevel
import com.mushroom.core.domain.entity.MushroomStatistics
import com.mushroom.core.domain.entity.ScoreStatistics
import com.mushroom.core.domain.entity.ScoreTrend
import com.mushroom.core.domain.entity.StatisticsPeriod
import com.mushroom.core.domain.entity.Subject
import com.mushroom.feature.statistics.viewmodel.StatisticsViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatisticsScreen(
    viewModel: StatisticsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var tabIndex by remember { mutableStateOf(0) }

    Scaffold(
        topBar = { CenterAlignedTopAppBar(title = { Text("数据统计") }) }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // 时间段选择
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

            // Tabs
            TabRow(selectedTabIndex = tabIndex) {
                Tab(selected = tabIndex == 0, onClick = { tabIndex = 0 }, text = { Text("学习情况") })
                Tab(selected = tabIndex == 1, onClick = { tabIndex = 1 }, text = { Text("蘑菇收支") })
                Tab(selected = tabIndex == 2, onClick = { tabIndex = 2 }, text = { Text("成绩趋势") })
            }

            if (uiState.isLoading) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("加载中…")
                }
                return@Scaffold
            }

            when (tabIndex) {
                0 -> CheckInTab(stats = uiState.checkInStats)
                1 -> MushroomTab(stats = uiState.mushroomStats)
                2 -> ScoreTab(scoreStats = uiState.scoreStats)
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
        return
    }
    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        item {
            // Streak 卡片
            Card(Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    StreakItem("当前连续", "${stats.currentStreak} 天")
                    StreakItem("最长连续", "${stats.longestStreak} 天")
                    StreakItem("总打卡", "${stats.totalCheckins} 次")
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
            Text("暂无蘑菇数据")
        }
        return
    }
    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        item {
            // 当前余额
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text("当前余额", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(8.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                        MushroomLevel.values().forEach { level ->
                            val cnt = stats.currentBalance.get(level)
                            if (cnt > 0) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(mushroomEmoji(level))
                                    Text(cnt.toString(), fontWeight = FontWeight.Bold)
                                    Text(level.displayName, style = MaterialTheme.typography.labelSmall)
                                }
                            }
                        }
                    }
                }
            }
        }
        item {
            // 收支汇总
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text("期间收支", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(8.dp))
                    val totalEarned = stats.totalEarned.entries.sumOf { (level, cnt) -> level.exchangePoints * cnt }
                    val totalSpent = stats.totalSpent.entries.sumOf { (level, cnt) -> level.exchangePoints * cnt }
                    val totalDeducted = stats.totalDeducted.entries.sumOf { (level, cnt) -> level.exchangePoints * cnt }
                    Text("获得积分：+$totalEarned", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.tertiary)
                    Text("兑换消耗：-$totalSpent", style = MaterialTheme.typography.bodyMedium)
                    Text("扣除：-$totalDeducted", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}

// -----------------------------------------------------------------------
// Tab 3: 成绩趋势
// -----------------------------------------------------------------------
@Composable
private fun ScoreTab(scoreStats: Map<Subject, ScoreStatistics>) {
    var selectedSubject by remember { mutableStateOf(Subject.MATH) }
    val currentStats = scoreStats[selectedSubject]

    Column(Modifier.fillMaxSize()) {
        // 学科选择
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Subject.values().forEach { subject ->
                item {
                    FilterChip(
                        selected = selectedSubject == subject,
                        onClick = { selectedSubject = subject },
                        label = { Text(subjectLabel(subject)) }
                    )
                }
            }
        }

        if (currentStats == null || currentStats.scorePoints.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("${subjectLabel(selectedSubject)}暂无成绩记录")
            }
            return
        }

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
                                Text(trendLabel(currentStats.trend), style = MaterialTheme.typography.titleSmall, color = trendColor(currentStats.trend))
                                Text("趋势", style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                }
            }
            items(currentStats.scorePoints.reversed()) { point ->
                Card(Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text(point.name, style = MaterialTheme.typography.bodyMedium)
                            Text("${point.date} · ${milestoneTypeLabel(point.type)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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

private fun mushroomEmoji(level: MushroomLevel) = when (level) {
    MushroomLevel.SMALL -> "🍄"; MushroomLevel.MEDIUM -> "🍄‍🟫"; MushroomLevel.LARGE -> "🌟"
    MushroomLevel.GOLD -> "✨"; MushroomLevel.LEGEND -> "👑"
}

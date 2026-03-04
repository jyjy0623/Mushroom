package com.mushroom.feature.checkin.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mushroom.feature.checkin.viewmodel.CheckInCalendarViewEvent
import com.mushroom.feature.checkin.viewmodel.CheckInCalendarViewModel
import kotlinx.coroutines.flow.collectLatest
import java.time.LocalDate
import java.time.format.DateTimeFormatter

private val MONTH_FMT = DateTimeFormatter.ofPattern("yyyy年MM月")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CheckInCalendarScreen(
    onNavigateBack: () -> Unit = {},
    viewModel: CheckInCalendarViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(Unit) {
        viewModel.viewEvent.collectLatest { event ->
            when (event) {
                is CheckInCalendarViewEvent.ShowSnackbar -> snackbarHostState.showSnackbar(event.message)
                CheckInCalendarViewEvent.CheckInSuccess -> snackbarHostState.showSnackbar("打卡成功 🍄")
            }
        }
    }

    val displayMonth = uiState.displayMonth
    val summaryMap = uiState.summaryMap
    val currentStreak = uiState.currentStreak
    val longestStreak = uiState.longestStreak

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = { viewModel.navigateToPreviousMonth() }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "上个月")
                        }
                        Text(
                            text = displayMonth.format(MONTH_FMT),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        IconButton(onClick = { viewModel.navigateToNextMonth() }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = "下个月")
                        }
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp)
        ) {
            Spacer(Modifier.height(8.dp))

            StreakCard(currentStreak = currentStreak, longestStreak = longestStreak)

            Spacer(Modifier.height(16.dp))

            CalendarGrid(displayMonth = displayMonth, summaryMap = summaryMap)
        }
    }
}

@Composable
private fun StreakCard(currentStreak: Int, longestStreak: Int) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
    ) {
        Row(
            modifier = Modifier.padding(16.dp).fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            StreakItem(label = "当前连续", value = currentStreak, suffix = "天")
            StreakItem(label = "最长连续", value = longestStreak, suffix = "天")
        }
    }
}

@Composable
private fun StreakItem(label: String, value: Int, suffix: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, style = MaterialTheme.typography.bodySmall)
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                text = value.toString(),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            Text(suffix, style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(bottom = 4.dp))
        }
    }
}

@Composable
private fun CalendarGrid(displayMonth: LocalDate, summaryMap: Map<LocalDate, DayCheckInSummary>) {
    val firstDay = displayMonth.withDayOfMonth(1)
    val daysInMonth = displayMonth.lengthOfMonth()
    // 周几偏移（周一=0）
    val startOffset = (firstDay.dayOfWeek.value - 1) % 7

    val weekDayLabels = listOf("一", "二", "三", "四", "五", "六", "日")

    Column {
        // 星期标题行
        Row(modifier = Modifier.fillMaxWidth()) {
            weekDayLabels.forEach { label ->
                Text(
                    text = label,
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Spacer(Modifier.height(4.dp))

        val totalCells = startOffset + daysInMonth
        val rows = (totalCells + 6) / 7

        for (row in 0 until rows) {
            Row(modifier = Modifier.fillMaxWidth()) {
                for (col in 0 until 7) {
                    val cellIndex = row * 7 + col
                    val day = cellIndex - startOffset + 1
                    if (day < 1 || day > daysInMonth) {
                        Box(modifier = Modifier.weight(1f).aspectRatio(1f))
                    } else {
                        val date = displayMonth.withDayOfMonth(day)
                        val summary = summaryMap[date]
                        val isToday = date == LocalDate.now()
                        CalendarDay(
                            day = day,
                            isToday = isToday,
                            hasCheckIn = summary != null,
                            hasEarly = summary?.hasEarly == true,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
            Spacer(Modifier.height(4.dp))
        }
    }
}

@Composable
private fun CalendarDay(
    day: Int,
    isToday: Boolean,
    hasCheckIn: Boolean,
    hasEarly: Boolean,
    modifier: Modifier = Modifier
) {
    val bgColor = when {
        hasEarly -> MaterialTheme.colorScheme.tertiary.copy(alpha = 0.7f)
        hasCheckIn -> MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
        isToday -> MaterialTheme.colorScheme.primaryContainer
        else -> Color.Transparent
    }
    val textColor = when {
        hasCheckIn || hasEarly -> MaterialTheme.colorScheme.onPrimary
        isToday -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.onSurface
    }

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .aspectRatio(1f)
            .padding(2.dp)
            .clip(CircleShape)
            .background(bgColor)
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = day.toString(),
                style = MaterialTheme.typography.bodySmall,
                color = textColor,
                fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal
            )
            if (hasEarly) {
                Text("⚡", fontSize = 8.sp)
            }
        }
    }
}

// Type alias for convenience in this file
private typealias DayCheckInSummary = com.mushroom.feature.checkin.usecase.DayCheckInSummary

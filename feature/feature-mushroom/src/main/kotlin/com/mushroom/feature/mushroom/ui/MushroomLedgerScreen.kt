package com.mushroom.feature.mushroom.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mushroom.core.domain.entity.MushroomAction
import com.mushroom.core.domain.entity.MushroomBalance
import com.mushroom.core.domain.entity.MushroomLevel
import com.mushroom.core.domain.entity.MushroomTransaction
import com.mushroom.feature.mushroom.viewmodel.MushroomLedgerViewEvent
import com.mushroom.feature.mushroom.viewmodel.MushroomLedgerViewModel
import kotlinx.coroutines.flow.collectLatest
import java.time.LocalDate
import java.time.format.DateTimeFormatter

private val TIME_FMT = DateTimeFormatter.ofPattern("HH:mm")
private val DATE_LABEL_FMT = DateTimeFormatter.ofPattern("MM月dd日")
private val MONTH_LABEL_FMT = DateTimeFormatter.ofPattern("MM月")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MushroomLedgerScreen(
    viewModel: MushroomLedgerViewModel = hiltViewModel()
) {
    val balance by viewModel.balance.collectAsStateWithLifecycle()
    val ledger by viewModel.ledger.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(Unit) {
        viewModel.viewEvent.collectLatest { event ->
            when (event) {
                is MushroomLedgerViewEvent.ShowSnackbar -> snackbarHostState.showSnackbar(event.message)
            }
        }
    }

    val today = LocalDate.now()
    val currentYear = today.year
    val currentMonth = today.year * 100 + today.monthValue  // yyyyMM key

    // Group by date descending, then by year+month descending
    val groupedByDate = ledger
        .groupBy { it.createdAt.toLocalDate() }
        .toSortedMap(compareByDescending { it })

    // year → month → [dates]
    val yearToMonthToDateList: Map<Int, Map<Int, List<LocalDate>>> = groupedByDate.keys
        .groupBy { it.year }
        .toSortedMap(compareByDescending { it })
        .mapValues { (_, dates) ->
            dates.groupBy { it.monthValue }
                .toSortedMap(compareByDescending { it })
        }

    // Expansion state: current year expanded by default, others collapsed
    val expandedYears = rememberSaveable { mutableStateOf(setOf(currentYear)) }
    // Current month expanded by default
    val expandedMonths = rememberSaveable { mutableStateOf(setOf(currentMonth)) }
    // Today expanded by default
    val expandedDates = rememberSaveable { mutableStateOf(setOf(today.toString())) }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(title = { Text("蘑菇账本") })
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(vertical = 12.dp)
        ) {
            item {
                BalanceCard(balance = balance)
            }
            item {
                Text(
                    "流水记录",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
            if (ledger.isEmpty()) {
                item {
                    Text(
                        "暂无记录",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 16.dp)
                    )
                }
            } else {
                yearToMonthToDateList.forEach { (year, monthToDateList) ->
                    val isYearExpanded = year in expandedYears.value
                    val totalForYear = monthToDateList.values.flatten()
                        .sumOf { groupedByDate[it]?.size ?: 0 }

                    item(key = "year_$year") {
                        YearHeader(
                            year = year,
                            totalCount = totalForYear,
                            isExpanded = isYearExpanded,
                            onToggle = {
                                expandedYears.value = if (isYearExpanded)
                                    expandedYears.value - year
                                else
                                    expandedYears.value + year
                            }
                        )
                    }

                    if (isYearExpanded) {
                        monthToDateList.forEach { (month, dates) ->
                            val monthKey = year * 100 + month
                            val isMonthExpanded = monthKey in expandedMonths.value
                            val totalForMonth = dates.sumOf { groupedByDate[it]?.size ?: 0 }
                            val sampleDate = dates.first()

                            item(key = "month_${year}_$month") {
                                MonthHeader(
                                    date = sampleDate,
                                    totalCount = totalForMonth,
                                    isExpanded = isMonthExpanded,
                                    onToggle = {
                                        expandedMonths.value = if (isMonthExpanded)
                                            expandedMonths.value - monthKey
                                        else
                                            expandedMonths.value + monthKey
                                    }
                                )
                            }

                            if (isMonthExpanded) {
                                dates.forEach { date ->
                                    val txList = groupedByDate[date] ?: emptyList()
                                    val dateKey = date.toString()
                                    val isDateExpanded = dateKey in expandedDates.value

                                    item(key = "date_$dateKey") {
                                        DateHeader(
                                            date = date,
                                            today = today,
                                            count = txList.size,
                                            isExpanded = isDateExpanded,
                                            onToggle = {
                                                expandedDates.value = if (isDateExpanded)
                                                    expandedDates.value - dateKey
                                                else
                                                    expandedDates.value + dateKey
                                            }
                                        )
                                    }

                                    if (isDateExpanded) {
                                        txList.forEach { tx ->
                                            item(key = "tx_${tx.id}") {
                                                TransactionCard(tx = tx)
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
}

@Composable
private fun YearHeader(
    year: Int,
    totalCount: Int,
    isExpanded: Boolean,
    onToggle: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onToggle)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = if (isExpanded) Icons.Default.KeyboardArrowDown
                          else Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = if (isExpanded) "收起" else "展开",
            tint = MaterialTheme.colorScheme.primary
        )
        Text(
            text = "${year}年",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.weight(1f).padding(start = 4.dp)
        )
        Text(
            text = "$totalCount 条",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
    HorizontalDivider()
}

@Composable
private fun MonthHeader(
    date: LocalDate,
    totalCount: Int,
    isExpanded: Boolean,
    onToggle: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onToggle)
            .padding(start = 16.dp, top = 6.dp, bottom = 6.dp, end = 0.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = if (isExpanded) Icons.Default.KeyboardArrowDown
                          else Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = if (isExpanded) "收起" else "展开",
            tint = MaterialTheme.colorScheme.primary
        )
        Text(
            text = date.format(MONTH_LABEL_FMT),
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.weight(1f).padding(start = 4.dp)
        )
        Text(
            text = "$totalCount 条",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(end = 8.dp)
        )
    }
}

@Composable
private fun DateHeader(
    date: LocalDate,
    today: LocalDate,
    count: Int,
    isExpanded: Boolean,
    onToggle: () -> Unit
) {
    val label = when (date) {
        today -> "今天  ${date.format(DATE_LABEL_FMT)}"
        today.minusDays(1) -> "昨天  ${date.format(DATE_LABEL_FMT)}"
        else -> date.format(DATE_LABEL_FMT)
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onToggle)
            .padding(start = 16.dp, top = 6.dp, bottom = 6.dp, end = 0.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = if (isExpanded) Icons.Default.KeyboardArrowDown
                          else Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = if (isExpanded) "收起" else "展开",
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.weight(1f).padding(start = 4.dp)
        )
        Text(
            text = "$count 条",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(end = 8.dp)
        )
    }
}

@Composable
private fun BalanceCard(balance: MushroomBalance) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                "蘑菇余额",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                MushroomLevel.values().forEach { level ->
                    BalanceItem(level = level, count = balance.get(level))
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(
                "总积分：${balance.totalPoints()}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
        }
    }
}

@Composable
private fun BalanceItem(level: MushroomLevel, count: Int) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(mushroomEmoji(level), fontSize = 24.sp)
        Text(
            count.toString(),
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Bold
        )
        Text(level.displayName, style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
private fun TransactionCard(tx: MushroomTransaction) {
    val isEarn = tx.action == MushroomAction.EARN
    val color = if (isEarn) MaterialTheme.colorScheme.tertiary
                else MaterialTheme.colorScheme.error
    val sign = if (isEarn) "+" else "-"

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(mushroomEmoji(tx.level), fontSize = 20.sp, modifier = Modifier.padding(end = 8.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(tx.note ?: tx.sourceType.name, style = MaterialTheme.typography.bodyMedium)
                Text(
                    tx.createdAt.format(TIME_FMT),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(
                "$sign${tx.amount} ${tx.level.displayName}",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                color = color
            )
        }
    }
}

private fun mushroomEmoji(level: MushroomLevel) = when (level) {
    MushroomLevel.SMALL  -> "🍄"
    MushroomLevel.MEDIUM -> "🍄‍🟫"
    MushroomLevel.LARGE  -> "🌟"
    MushroomLevel.GOLD   -> "✨"
    MushroomLevel.LEGEND -> "👑"
}

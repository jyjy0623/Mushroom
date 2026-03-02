package com.mushroom.feature.mushroom.ui

import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
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
import java.time.format.DateTimeFormatter

private val DATE_FMT = DateTimeFormatter.ofPattern("MM-dd HH:mm")

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
                items(ledger, key = { it.id }) { tx ->
                    TransactionCard(tx = tx)
                }
            }
        }
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
                    val count = balance.get(level)
                    if (count > 0 || level == MushroomLevel.SMALL) {
                        BalanceItem(level = level, count = count)
                    }
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
        modifier = Modifier.fillMaxWidth(),
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
                    tx.createdAt.format(DATE_FMT),
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

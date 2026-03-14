package com.mushroom.feature.game.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mushroom.adventure.core.network.data.LeaderboardEntry
import com.mushroom.feature.game.entity.GameScore
import com.mushroom.feature.game.viewmodel.FriendsState
import com.mushroom.feature.game.viewmodel.GameViewModel
import com.mushroom.feature.game.viewmodel.GlobalLeaderboardState
import java.time.format.DateTimeFormatter

private val DATE_FMT = DateTimeFormatter.ofPattern("MM-dd HH:mm")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LeaderboardScreen(
    onBack: () -> Unit,
    viewModel: GameViewModel = hiltViewModel()
) {
    val localScores by viewModel.topScores.collectAsStateWithLifecycle()
    val globalState by viewModel.globalLeaderboard.collectAsStateWithLifecycle()
    val friendState by viewModel.friendLeaderboard.collectAsStateWithLifecycle()
    val friendsState by viewModel.friendsState.collectAsStateWithLifecycle()
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }
    var showFriendDialog by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        viewModel.loadGlobalLeaderboard()
    }

    if (showFriendDialog) {
        FriendManagementDialog(
            friendsState = friendsState,
            onDismiss = { showFriendDialog = false },
            onAddFriend = viewModel::addFriend,
            onRemoveFriend = viewModel::removeFriend,
            onClearAddResult = viewModel::clearAddResult
        )
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("排行榜") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    if (selectedTab == 2) {
                        TextButton(onClick = {
                            viewModel.loadFriends()
                            showFriendDialog = true
                        }) {
                            Text("好友管理")
                        }
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            TabRow(selectedTabIndex = selectedTab) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = { Text("本地记录") }
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = {
                        selectedTab = 1
                        viewModel.loadGlobalLeaderboard()
                    },
                    text = { Text("全球排行") }
                )
                Tab(
                    selected = selectedTab == 2,
                    onClick = {
                        selectedTab = 2
                        viewModel.loadFriendLeaderboard()
                    },
                    text = { Text("好友排行") }
                )
            }

            when (selectedTab) {
                0 -> LocalLeaderboardTab(scores = localScores)
                1 -> GlobalLeaderboardTab(state = globalState, onRetry = { viewModel.loadGlobalLeaderboard() })
                2 -> FriendLeaderboardTab(
                    state = friendState,
                    onRetry = { viewModel.loadFriendLeaderboard() },
                    onManageFriends = {
                        viewModel.loadFriends()
                        showFriendDialog = true
                    }
                )
            }
        }
    }
}

@Composable
private fun LocalLeaderboardTab(scores: List<GameScore>) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
    ) {
        if (scores.isEmpty()) {
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 64.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
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
            item { Spacer(Modifier.height(8.dp)) }
            itemsIndexed(scores) { index, score ->
                val rank = index + 1
                val rankEmoji = when (rank) {
                    1 -> "🥇"
                    2 -> "🥈"
                    3 -> "🥉"
                    else -> "$rank."
                }
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
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
                            text = rankEmoji,
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
                            text = score.playedAt.format(DATE_FMT),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            item { Spacer(Modifier.height(16.dp)) }
        }
    }
}

@Composable
fun GlobalLeaderboardTab(
    state: GlobalLeaderboardState,
    onRetry: () -> Unit = {}
) {
    when {
        state.isLoading -> {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(modifier = Modifier.size(40.dp))
            }
        }
        state.error != null -> {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(vertical = 64.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(state.error!!, style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(16.dp))
                TextButton(onClick = onRetry) {
                    Text("重试")
                }
            }
        }
        state.entries.isEmpty() -> {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(vertical = 64.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("暂无排行数据", style = MaterialTheme.typography.bodyLarge)
                Spacer(Modifier.height(8.dp))
                Text(
                    "登录后游戏结束会自动提交分数",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        else -> {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp),
            ) {
                item { Spacer(Modifier.height(8.dp)) }
                itemsIndexed(state.entries) { _, entry ->
                    GlobalLeaderboardRow(
                        entry = entry,
                        isMe = state.myEntry?.userId == entry.userId
                    )
                }

                // 底部显示"我的排名"（若不在 Top 列表中）
                if (state.myEntry != null && state.entries.none { it.userId == state.myEntry.userId }) {
                    item {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "我的排名",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp)
                        )
                        GlobalLeaderboardRow(entry = state.myEntry, isMe = true)
                    }
                }

                item { Spacer(Modifier.height(16.dp)) }
            }
        }
    }
}

@Composable
private fun GlobalLeaderboardRow(entry: LeaderboardEntry, isMe: Boolean) {
    val rankEmoji = when (entry.rank) {
        1 -> "🥇"
        2 -> "🥈"
        3 -> "🥉"
        else -> "${entry.rank}."
    }
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        colors = CardDefaults.cardColors(
            containerColor = when {
                isMe -> MaterialTheme.colorScheme.tertiaryContainer
                entry.rank <= 3 -> MaterialTheme.colorScheme.primaryContainer
                else -> MaterialTheme.colorScheme.surface
            }
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = rankEmoji,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.width(40.dp)
            )
            Text(
                text = entry.nickname.ifBlank { "匿名" },
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = "${entry.score} 分",
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@Composable
private fun FriendLeaderboardTab(
    state: GlobalLeaderboardState,
    onRetry: () -> Unit,
    onManageFriends: () -> Unit
) {
    when {
        state.isLoading -> {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(modifier = Modifier.size(40.dp))
            }
        }
        state.error != null -> {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(vertical = 64.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(state.error!!, style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(16.dp))
                TextButton(onClick = onRetry) {
                    Text("重试")
                }
            }
        }
        state.entries.isEmpty() -> {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(vertical = 64.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("还没有好友排行数据", style = MaterialTheme.typography.bodyLarge)
                Spacer(Modifier.height(8.dp))
                Text(
                    "添加好友一起比赛吧！",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(16.dp))
                TextButton(onClick = onManageFriends) {
                    Text("添加好友")
                }
            }
        }
        else -> {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp),
            ) {
                item { Spacer(Modifier.height(8.dp)) }
                itemsIndexed(state.entries) { _, entry ->
                    GlobalLeaderboardRow(
                        entry = entry,
                        isMe = state.myEntry?.userId == entry.userId
                    )
                }
                item { Spacer(Modifier.height(16.dp)) }
            }
        }
    }
}

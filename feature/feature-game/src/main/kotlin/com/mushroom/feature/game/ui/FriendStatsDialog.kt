package com.mushroom.feature.game.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.mushroom.feature.game.viewmodel.FriendStatsState

@Composable
fun FriendStatsDialog(
    state: FriendStatsState,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(state.stats?.nickname?.ifBlank { "匿名" } ?: "好友统计")
        },
        text = {
            when {
                state.isLoading -> {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier
                                .padding(32.dp)
                                .size(40.dp)
                        )
                    }
                }
                state.error != null -> {
                    Text(
                        text = state.error,
                        color = MaterialTheme.colorScheme.error
                    )
                }
                state.stats != null -> {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        // 学习情况
                        SectionTitle("学习情况")
                        StatRow("当前连续打卡", "${state.stats.currentStreak} 天")
                        StatRow("最长连续打卡", "${state.stats.longestStreak} 天")
                        StatRow("总打卡次数", "${state.stats.totalCheckins} 次")

                        Spacer(Modifier.height(12.dp))
                        HorizontalDivider()
                        Spacer(Modifier.height(12.dp))

                        // 蘑菇积分
                        SectionTitle("蘑菇积分")
                        StatRow("总积分", "${state.stats.totalMushroomPoints} 分")

                        Spacer(Modifier.height(12.dp))
                        HorizontalDivider()
                        Spacer(Modifier.height(12.dp))

                        // 游戏数据
                        SectionTitle("跑酷游戏")
                        StatRow(
                            "最高分",
                            state.stats.bestScore?.let { "$it 分" } ?: "暂无记录"
                        )
                        StatRow(
                            "全球排名",
                            state.stats.globalRank?.let { "第 $it 名" } ?: "暂无排名"
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("关闭")
            }
        }
    )
}

@Composable
private fun SectionTitle(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary
    )
    Spacer(Modifier.height(8.dp))
}

@Composable
private fun StatRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold
        )
    }
}

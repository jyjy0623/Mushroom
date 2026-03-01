package com.mushroom.adventure.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument

@Composable
fun AppNavGraph(
    navController: NavHostController,
    modifier: Modifier = Modifier
) {
    NavHost(
        navController = navController,
        startDestination = AppDestination.DailyTaskList.route,
        modifier = modifier
    ) {
        composable(AppDestination.DailyTaskList.route) {
            PlaceholderScreen("每日任务列表")
        }
        composable(AppDestination.MushroomLedger.route) {
            PlaceholderScreen("蘑菇账本")
        }
        composable(AppDestination.RewardList.route) {
            PlaceholderScreen("奖品列表")
        }
        composable(AppDestination.Statistics.route) {
            PlaceholderScreen("数据统计")
        }
        composable(
            route = AppDestination.TaskEdit.route,
            arguments = listOf(navArgument(AppDestination.TaskEdit.ARG_TASK_ID) {
                type = NavType.LongType
            })
        ) {
            PlaceholderScreen("任务编辑")
        }
        composable(AppDestination.TaskTemplate.route) {
            PlaceholderScreen("任务模板")
        }
        composable(AppDestination.CheckInHistory.route) {
            PlaceholderScreen("打卡历史")
        }
        composable(
            route = AppDestination.RewardDetail.route,
            arguments = listOf(navArgument(AppDestination.RewardDetail.ARG_REWARD_ID) {
                type = NavType.LongType
            })
        ) {
            PlaceholderScreen("奖品详情")
        }
        composable(AppDestination.MilestoneList.route) {
            PlaceholderScreen("里程碑列表")
        }
        composable(
            route = AppDestination.MilestoneEdit.route,
            arguments = listOf(navArgument(AppDestination.MilestoneEdit.ARG_MILESTONE_ID) {
                type = NavType.LongType
            })
        ) {
            PlaceholderScreen("里程碑编辑")
        }
        composable(AppDestination.DeductionRecord.route) {
            PlaceholderScreen("扣分记录")
        }
        composable(AppDestination.DeductionConfig.route) {
            PlaceholderScreen("扣分配置")
        }
        composable(AppDestination.Settings.route) {
            PlaceholderScreen("设置")
        }
    }
}

@Composable
private fun PlaceholderScreen(name: String) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Text(text = name)
    }
}

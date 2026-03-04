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
import com.mushroom.adventure.ui.settings.SettingsScreen
import com.mushroom.adventure.update.UpdateViewModel
import com.mushroom.feature.checkin.ui.CheckInCalendarScreen
import com.mushroom.feature.milestone.ui.MilestoneEditScreen
import com.mushroom.feature.milestone.ui.MilestoneListScreen
import com.mushroom.feature.mushroom.ui.DeductionConfigScreen
import com.mushroom.feature.mushroom.ui.DeductionRecordScreen
import com.mushroom.feature.mushroom.ui.MushroomLedgerScreen
import com.mushroom.feature.reward.ui.RewardCreateScreen
import com.mushroom.feature.reward.ui.RewardDetailScreen
import com.mushroom.feature.reward.ui.RewardListScreen
import com.mushroom.feature.statistics.ui.StatisticsScreen
import com.mushroom.feature.task.ui.DailyTaskListScreen
import com.mushroom.feature.task.ui.TaskEditScreen
import com.mushroom.feature.task.ui.TaskTemplateScreen
import java.time.LocalDate

@Composable
fun AppNavGraph(
    navController: NavHostController,
    updateViewModel: UpdateViewModel,
    modifier: Modifier = Modifier
) {
    NavHost(
        navController = navController,
        startDestination = AppDestination.DailyTaskList.route,
        modifier = modifier
    ) {
        // ---- 任务模块 ----
        composable(AppDestination.DailyTaskList.route) {
            DailyTaskListScreen(
                onNavigateToAddTask = { dateIso ->
                    navController.navigate(AppDestination.TaskEdit.route(date = dateIso))
                },
                onNavigateToEditTask = { taskId ->
                    navController.navigate(AppDestination.TaskEdit.route(taskId))
                },
                onNavigateToTemplates = {
                    navController.navigate(AppDestination.TaskTemplate.route)
                },
                onNavigateToAddMilestone = {
                    navController.navigate(AppDestination.MilestoneList.route)
                },
                onNavigateToCheckInHistory = {
                    navController.navigate(AppDestination.CheckInHistory.route)
                }
            )
        }
        composable(
            route = AppDestination.TaskEdit.route,
            arguments = listOf(
                navArgument(AppDestination.TaskEdit.ARG_TASK_ID) {
                    type = NavType.LongType
                    defaultValue = -1L
                },
                navArgument(AppDestination.TaskEdit.ARG_DATE) {
                    type = NavType.StringType
                    defaultValue = ""
                }
            )
        ) { backStackEntry ->
            val dateStr = backStackEntry.arguments?.getString(AppDestination.TaskEdit.ARG_DATE)
            val date = dateStr?.takeIf { it.isNotEmpty() }?.let {
                runCatching { LocalDate.parse(it) }.getOrNull()
            } ?: LocalDate.now()
            TaskEditScreen(
                date = date,
                onNavigateBack = { navController.popBackStack() }
            )
        }
        composable(AppDestination.TaskTemplate.route) {
            TaskTemplateScreen(
                onNavigateBack = { navController.popBackStack() },
                onTemplateApplied = { navController.popBackStack() },
                onNavigateToMilestoneList = {
                    navController.navigate(AppDestination.MilestoneList.route)
                },
                onNavigateToMilestoneCreate = {
                    navController.navigate(AppDestination.MilestoneEdit.route())
                }
            )
        }

        // ---- 蘑菇 ----
        composable(AppDestination.MushroomLedger.route) {
            MushroomLedgerScreen()
        }

        // ---- 奖品 ----
        composable(AppDestination.RewardList.route) {
            RewardListScreen(
                onNavigateToDetail = { rewardId ->
                    navController.navigate(AppDestination.RewardDetail.route(rewardId))
                },
                onNavigateToCreate = {
                    navController.navigate(AppDestination.RewardCreate.route)
                }
            )
        }
        composable(AppDestination.RewardCreate.route) {
            RewardCreateScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }
        composable(
            route = AppDestination.RewardDetail.route,
            arguments = listOf(navArgument(AppDestination.RewardDetail.ARG_REWARD_ID) {
                type = NavType.LongType
            })
        ) { backStackEntry ->
            val rewardId = backStackEntry.arguments?.getLong(AppDestination.RewardDetail.ARG_REWARD_ID)
                ?: return@composable
            RewardDetailScreen(
                rewardId = rewardId,
                onNavigateBack = { navController.popBackStack() }
            )
        }

        // ---- 统计 ----
        composable(AppDestination.Statistics.route) {
            StatisticsScreen()
        }

        // ---- 打卡历史 ----
        composable(AppDestination.CheckInHistory.route) {
            CheckInCalendarScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }

        // ---- 里程碑 ----
        composable(AppDestination.MilestoneList.route) {
            MilestoneListScreen(
                onNavigateToEdit = { milestoneId ->
                    navController.navigate(AppDestination.MilestoneEdit.route(milestoneId))
                },
                onNavigateToCreate = {
                    navController.navigate(AppDestination.MilestoneEdit.route())
                }
            )
        }
        composable(
            route = AppDestination.MilestoneEdit.route,
            arguments = listOf(navArgument(AppDestination.MilestoneEdit.ARG_MILESTONE_ID) {
                type = NavType.LongType
            })
        ) {
            MilestoneEditScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }

        // ---- 扣分 ----
        composable(AppDestination.DeductionRecord.route) {
            DeductionRecordScreen()
        }
        composable(AppDestination.DeductionConfig.route) {
            DeductionConfigScreen()
        }

        // ---- 设置（Sprint 5 实装）----
        composable(AppDestination.Settings.route) {
            SettingsScreen(
                onCheckUpdate = {
                    updateViewModel.checkForUpdate(forceShow = true)
                }
            )
        }
    }
}

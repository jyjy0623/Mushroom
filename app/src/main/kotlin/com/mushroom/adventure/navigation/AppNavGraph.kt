package com.mushroom.adventure.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.mushroom.adventure.ui.CreateScreen
import com.mushroom.adventure.ui.settings.SettingsScreen
import com.mushroom.adventure.update.UpdateViewModel
import com.mushroom.core.logging.MushroomLogger
import com.mushroom.feature.checkin.ui.CheckInCalendarScreen
// MilestoneEditScreen 已由 CreateScreen(Tab[1]) 取代，暂保留文件备用
import com.mushroom.feature.milestone.ui.MilestoneEditScreen
import com.mushroom.feature.milestone.ui.MilestoneListScreen
import com.mushroom.feature.mushroom.ui.DeductionConfigScreen
import com.mushroom.feature.mushroom.ui.DeductionRecordScreen
import com.mushroom.feature.mushroom.ui.KeyDateEditScreen
import com.mushroom.feature.mushroom.ui.KeyDateListScreen
import com.mushroom.feature.mushroom.ui.MushroomLedgerScreen
import com.mushroom.feature.reward.ui.RewardCreateScreen
import com.mushroom.feature.reward.ui.RewardDetailScreen
import com.mushroom.feature.reward.ui.RewardListScreen
import com.mushroom.feature.statistics.ui.StatisticsScreen
import com.mushroom.feature.task.ui.DailyTaskListScreen
import com.mushroom.feature.task.ui.TaskEditScreen
import com.mushroom.feature.task.ui.TaskTemplateScreen
import java.time.LocalDate

private const val NAV_TAG = "AppNavGraph"

/**
 * 安全导航封装：捕获 IllegalArgumentException（目标路由不存在）并记录详细错误日志，
 * 避免因路由拼写或参数格式错误直接导致崩溃。
 */
private fun NavHostController.safeNavigate(route: String) {
    MushroomLogger.d(NAV_TAG, "navigate → $route")
    try {
        navigate(route)
    } catch (e: IllegalArgumentException) {
        MushroomLogger.e(
            NAV_TAG,
            "导航失败：目标路由 \"$route\" 不存在于导航图中。" +
                "当前路由栈：${currentBackStack.value.map { it.destination.route }}",
            e
        )
    } catch (e: Exception) {
        MushroomLogger.e(NAV_TAG, "导航异常：route=\"$route\"", e)
    }
}

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
                    // 新建任务 → 合并页 Tab[0]
                    navController.safeNavigate(AppDestination.Create.route(date = dateIso, initialTab = 0))
                },
                onNavigateToEditTask = { taskId ->
                    // 编辑任务 → 原 TaskEdit 页（date 传今日，用于截止时间选择基准）
                    navController.safeNavigate(AppDestination.TaskEdit.route(taskId, LocalDate.now().toString()))
                },
                onNavigateToTemplates = {
                    navController.safeNavigate(AppDestination.TaskTemplate.route)
                },
                onNavigateToAddMilestone = {
                    // 新建里程碑 → 合并页 Tab[1]
                    navController.safeNavigate(AppDestination.Create.route(initialTab = 1))
                },
                onNavigateToCheckInHistory = {
                    navController.safeNavigate(AppDestination.CheckInHistory.route)
                },
                onNavigateToMilestoneList = {
                    navController.safeNavigate(AppDestination.MilestoneList.route)
                }
            )
        }

        // 合并新建页（新建任务 + 新建里程碑）
        composable(
            route = AppDestination.Create.route,
            arguments = listOf(
                navArgument(AppDestination.Create.ARG_DATE) {
                    type = NavType.StringType
                    defaultValue = ""
                },
                navArgument(AppDestination.Create.ARG_INITIAL_TAB) {
                    type = NavType.IntType
                    defaultValue = 0
                }
            )
        ) { backStackEntry ->
            val dateStr = backStackEntry.arguments?.getString(AppDestination.Create.ARG_DATE)
            val date = dateStr?.takeIf { it.isNotEmpty() }?.let {
                runCatching { LocalDate.parse(it) }.getOrElse { t ->
                    MushroomLogger.w(NAV_TAG, "Create 页日期参数解析失败：\"$it\"", t)
                    null
                }
            } ?: LocalDate.now()
            val initialTab = backStackEntry.arguments?.getInt(AppDestination.Create.ARG_INITIAL_TAB) ?: 0
            CreateScreen(
                date = date,
                initialTab = initialTab,
                onNavigateBack = { navController.popBackStack() }
            )
        }

        // 编辑/只读任务页（taskId 必须 > 0）
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
            val taskId = backStackEntry.arguments?.getLong(AppDestination.TaskEdit.ARG_TASK_ID) ?: -1L
            val dateStr = backStackEntry.arguments?.getString(AppDestination.TaskEdit.ARG_DATE)
            val date = dateStr?.takeIf { it.isNotEmpty() }?.let {
                runCatching { LocalDate.parse(it) }.getOrElse { t ->
                    MushroomLogger.w(NAV_TAG, "TaskEdit 页日期参数解析失败：\"$it\"", t)
                    null
                }
            } ?: LocalDate.now()
            MushroomLogger.d(NAV_TAG, "TaskEdit：taskId=$taskId, date=$date")
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
                    navController.safeNavigate(AppDestination.MilestoneList.route)
                },
                onNavigateToMilestoneCreate = {
                    navController.safeNavigate(AppDestination.Create.route(initialTab = 1))
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
                    navController.safeNavigate(AppDestination.RewardDetail.route(rewardId))
                },
                onNavigateToCreate = {
                    navController.safeNavigate(AppDestination.RewardCreate.route)
                }
            )
        }
        composable(AppDestination.RewardCreate.route) {
            RewardCreateScreen(onNavigateBack = { navController.popBackStack() })
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
            CheckInCalendarScreen(onNavigateBack = { navController.popBackStack() })
        }

        // ---- 里程碑 ----
        composable(AppDestination.MilestoneList.route) {
            MilestoneListScreen(
                onNavigateToEdit = { milestoneId ->
                    navController.safeNavigate(AppDestination.MilestoneEdit.route(milestoneId))
                },
                onNavigateToCreate = {
                    navController.safeNavigate(AppDestination.Create.route(initialTab = 1))
                }
            )
        }
        // MilestoneEditScreen 用于编辑已有里程碑（milestoneId > 0）
        composable(
            route = AppDestination.MilestoneEdit.route,
            arguments = listOf(navArgument(AppDestination.MilestoneEdit.ARG_MILESTONE_ID) {
                type = NavType.LongType
                defaultValue = -1L
            })
        ) {
            MilestoneEditScreen(onNavigateBack = { navController.popBackStack() })
        }

        // ---- 扣分 ----
        composable(AppDestination.DeductionRecord.route) {
            DeductionRecordScreen(onNavigateBack = { navController.popBackStack() })
        }
        composable(AppDestination.DeductionConfig.route) {
            DeductionConfigScreen(onNavigateBack = { navController.popBackStack() })
        }

        // ---- 关键奖励时间 ----
        composable(AppDestination.KeyDateList.route) {
            KeyDateListScreen(
                onNavigateBack = { navController.popBackStack() },
                onNavigateToEdit = { id ->
                    navController.safeNavigate(AppDestination.KeyDateEdit.route(id))
                },
                onNavigateToCreate = {
                    navController.safeNavigate(AppDestination.KeyDateEdit.route())
                }
            )
        }
        composable(
            route = AppDestination.KeyDateEdit.route,
            arguments = listOf(navArgument(AppDestination.KeyDateEdit.ARG_KEY_DATE_ID) {
                type = NavType.LongType
                defaultValue = -1L
            })
        ) { backStackEntry ->
            val keyDateId = backStackEntry.arguments?.getLong(AppDestination.KeyDateEdit.ARG_KEY_DATE_ID) ?: -1L
            KeyDateEditScreen(
                keyDateId = keyDateId,
                onNavigateBack = { navController.popBackStack() }
            )
        }

        // ---- 设置 ----
        composable(AppDestination.Settings.route) {
            SettingsScreen(
                onCheckUpdate = {
                    updateViewModel.checkForUpdate(forceShow = true)
                },
                onNavigateToDeductionConfig = {
                    navController.safeNavigate(AppDestination.DeductionConfig.route)
                },
                onNavigateToDeductionRecord = {
                    navController.safeNavigate(AppDestination.DeductionRecord.route)
                },
                onNavigateToKeyDateList = {
                    navController.safeNavigate(AppDestination.KeyDateList.route)
                }
            )
        }
    }
}

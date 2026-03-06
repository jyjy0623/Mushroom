package com.mushroom.adventure.navigation

sealed class AppDestination(val route: String) {
    // 主要页面（底部导航）
    object DailyTaskList : AppDestination("daily_task_list")
    object MushroomLedger : AppDestination("mushroom_ledger")
    object RewardList : AppDestination("reward_list")
    object Statistics : AppDestination("statistics")

    // 任务相关
    object Create : AppDestination("create/{date}/{initialTab}") {
        const val ARG_DATE = "date"
        const val ARG_INITIAL_TAB = "initialTab"
        fun route(date: String = "", initialTab: Int = 0) = "create/$date/$initialTab"
    }
    object TaskEdit : AppDestination("task_edit/{taskId}/{date}") {
        const val ARG_TASK_ID = "taskId"
        const val ARG_DATE = "date"
        fun route(taskId: Long = -1L, date: String = "") = "task_edit/$taskId/$date"
    }
    object TaskTemplate : AppDestination("task_template")

    // 打卡相关
    object CheckInHistory : AppDestination("checkin_history")

    // 奖品相关
    object RewardCreate : AppDestination("reward_create")
    object RewardDetail : AppDestination("reward_detail/{rewardId}") {
        const val ARG_REWARD_ID = "rewardId"
        fun route(rewardId: Long) = "reward_detail/$rewardId"
    }

    // 里程碑相关
    object MilestoneList : AppDestination("milestone_list")
    object MilestoneEdit : AppDestination("milestone_edit/{milestoneId}") {
        const val ARG_MILESTONE_ID = "milestoneId"
        fun route(milestoneId: Long = -1L) = "milestone_edit/$milestoneId"
    }

    // 扣分相关
    object DeductionRecord : AppDestination("deduction_record")
    object DeductionConfig : AppDestination("deduction_config")

    // 关键奖励时间
    object KeyDateList : AppDestination("key_date_list")
    object KeyDateEdit : AppDestination("key_date_edit/{keyDateId}") {
        const val ARG_KEY_DATE_ID = "keyDateId"
        fun route(keyDateId: Long = -1L) = "key_date_edit/$keyDateId"
    }

    // 设置
    object Settings : AppDestination("settings")

    // 游戏
    object Game : AppDestination("game")
}

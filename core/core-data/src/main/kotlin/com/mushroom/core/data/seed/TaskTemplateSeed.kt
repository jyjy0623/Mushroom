package com.mushroom.core.data.seed

import com.mushroom.core.data.db.dao.TaskTemplateDao
import com.mushroom.core.data.db.entity.TaskTemplateEntity
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 内置任务模板 Seed 数据。
 * 应用首次启动时调用 seed()，使用 OnConflictStrategy.IGNORE 保证幂等性。
 */
@Singleton
class TaskTemplateSeed @Inject constructor(
    private val templateDao: TaskTemplateDao
) {
    suspend fun seed() {
        val builtIn = listOf(
            TaskTemplateEntity(
                id = 1L,                               // 固定 id，保证幂等
                name = "晨读",
                type = "MORNING_READING",
                subject = "CHINESE",
                estimatedMinutes = 20,
                description = "每天早晨朗读课文或阅读材料，培养语感和阅读习惯。",
                defaultDeadlineOffset = 7 * 60,
                baseRewardLevel = "SMALL",
                baseRewardAmount = 1,
                bonusRewardLevel = "SMALL",
                bonusRewardAmount = 1,
                bonusConditionType = "CONSECUTIVE_DAYS",
                bonusConditionValue = 7,
                isBuiltIn = true
            ),
            TaskTemplateEntity(
                id = 2L,                               // 固定 id，保证幂等
                name = "备忘录",
                type = "HOMEWORK_MEMO",
                subject = "OTHER",
                estimatedMinutes = 5,
                description = "记录当天所有作业，确保不遗漏。",
                defaultDeadlineOffset = 15 * 60,
                baseRewardLevel = "SMALL",
                baseRewardAmount = 1,
                bonusRewardLevel = null,
                bonusRewardAmount = null,
                bonusConditionType = null,
                bonusConditionValue = null,
                isBuiltIn = true
            ),
            TaskTemplateEntity(
                id = 3L,                               // 固定 id，保证幂等
                name = "在校完成作业",
                type = "HOMEWORK_AT_SCHOOL",
                subject = "OTHER",
                estimatedMinutes = 60,
                description = "利用课间或自习课在学校完成当天作业，减轻回家负担。",
                defaultDeadlineOffset = 17 * 60,
                baseRewardLevel = "SMALL",
                baseRewardAmount = 2,
                bonusRewardLevel = "MEDIUM",
                bonusRewardAmount = 1,
                bonusConditionType = "WITHIN_MINUTES",
                bonusConditionValue = 180,
                isBuiltIn = true
            )
        )
        // IGNORE 策略：已存在则跳过，不会重复插入
        builtIn.forEach { templateDao.insert(it) }
    }
}

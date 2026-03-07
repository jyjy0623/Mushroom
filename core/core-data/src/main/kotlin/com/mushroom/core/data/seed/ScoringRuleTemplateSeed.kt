package com.mushroom.core.data.seed

import com.mushroom.core.data.db.dao.ScoringRuleTemplateDao
import com.mushroom.core.data.db.entity.ScoringRuleTemplateEntity
import com.mushroom.core.data.db.entity.ScoringRuleTemplateItemEntity
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 内置评分规则模板 Seed 数据。
 * 应用首次启动时调用 seed()，使用固定 id + REPLACE 策略保证幂等性。
 *
 * 内置三个模板（对应 DefaultScoringRules 中的三套规则）：
 *   id=1  小测 / 周自测
 *   id=2  校内考试
 *   id=3  期中 / 期末
 */
@Singleton
class ScoringRuleTemplateSeed @Inject constructor(
    private val dao: ScoringRuleTemplateDao
) {
    suspend fun seed() {
        // 内置模板头
        val templates = listOf(
            ScoringRuleTemplateEntity(id = 1L, name = "小测 / 周自测", isBuiltIn = true),
            ScoringRuleTemplateEntity(id = 2L, name = "校内考试",      isBuiltIn = true),
            ScoringRuleTemplateEntity(id = 3L, name = "期中 / 期末",   isBuiltIn = true),
        )
        templates.forEach { dao.insertTemplate(it) }

        // 内置模板规则行（先删后插，保证升级也能刷新）
        val items = listOf(
            // id=1  小测 / 周自测  →  90-100:中蘑菇×2 / 80-89:中蘑菇×1 / 60-79:小蘑菇×1 / 0-59:小蘑菇×0
            ScoringRuleTemplateItemEntity(templateId = 1L, minScore = 90, maxScore = 100, rewardLevel = "MEDIUM", rewardAmount = 2),
            ScoringRuleTemplateItemEntity(templateId = 1L, minScore = 80, maxScore =  89, rewardLevel = "MEDIUM", rewardAmount = 1),
            ScoringRuleTemplateItemEntity(templateId = 1L, minScore = 60, maxScore =  79, rewardLevel = "SMALL",  rewardAmount = 1),
            ScoringRuleTemplateItemEntity(templateId = 1L, minScore =  0, maxScore =  59, rewardLevel = "SMALL",  rewardAmount = 0),
            // id=2  校内考试  →  90-100:金蘑菇×5 / 80-89:金蘑菇×3 / 60-79:大蘑菇×1 / 0-59:小蘑菇×0
            ScoringRuleTemplateItemEntity(templateId = 2L, minScore = 90, maxScore = 100, rewardLevel = "GOLD",   rewardAmount = 5),
            ScoringRuleTemplateItemEntity(templateId = 2L, minScore = 80, maxScore =  89, rewardLevel = "GOLD",   rewardAmount = 3),
            ScoringRuleTemplateItemEntity(templateId = 2L, minScore = 60, maxScore =  79, rewardLevel = "LARGE",  rewardAmount = 1),
            ScoringRuleTemplateItemEntity(templateId = 2L, minScore =  0, maxScore =  59, rewardLevel = "SMALL",  rewardAmount = 0),
            // id=3  期中 / 期末  →  90-100:传奇蘑菇×1 / 80-89:金蘑菇×8 / 60-79:大蘑菇×2 / 0-59:小蘑菇×0
            ScoringRuleTemplateItemEntity(templateId = 3L, minScore = 90, maxScore = 100, rewardLevel = "LEGEND", rewardAmount = 1),
            ScoringRuleTemplateItemEntity(templateId = 3L, minScore = 80, maxScore =  89, rewardLevel = "GOLD",   rewardAmount = 8),
            ScoringRuleTemplateItemEntity(templateId = 3L, minScore = 60, maxScore =  79, rewardLevel = "LARGE",  rewardAmount = 2),
            ScoringRuleTemplateItemEntity(templateId = 3L, minScore =  0, maxScore =  59, rewardLevel = "SMALL",  rewardAmount = 0),
        )
        // 先清除三个内置模板的旧 items，再重新插入（保证规则内容始终最新）
        listOf(1L, 2L, 3L).forEach { dao.deleteItemsByTemplateId(it) }
        dao.insertItems(items)
    }
}

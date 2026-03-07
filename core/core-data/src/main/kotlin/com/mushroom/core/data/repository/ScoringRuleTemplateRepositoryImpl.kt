package com.mushroom.core.data.repository

import com.mushroom.core.data.db.dao.ScoringRuleTemplateDao
import com.mushroom.core.data.db.entity.ScoringRuleTemplateEntity
import com.mushroom.core.data.db.entity.ScoringRuleTemplateItemEntity
import com.mushroom.core.domain.entity.MushroomLevel
import com.mushroom.core.domain.entity.MushroomRewardConfig
import com.mushroom.core.domain.entity.ScoringRule
import com.mushroom.core.domain.entity.ScoringRuleTemplate
import com.mushroom.core.domain.repository.ScoringRuleTemplateRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ScoringRuleTemplateRepositoryImpl @Inject constructor(
    private val dao: ScoringRuleTemplateDao
) : ScoringRuleTemplateRepository {

    override fun getAll(): Flow<List<ScoringRuleTemplate>> =
        dao.getAll().map { entities ->
            entities.map { entity ->
                val items = dao.getItemsByTemplateId(entity.id)
                entity.toDomain(items)
            }
        }

    override suspend fun insert(t: ScoringRuleTemplate): Long {
        val id = dao.insertTemplate(ScoringRuleTemplateEntity(name = t.name))
        dao.insertItems(t.rules.map { it.toEntity(id) })
        return id
    }

    override suspend fun update(t: ScoringRuleTemplate) {
        dao.updateTemplate(ScoringRuleTemplateEntity(id = t.id, name = t.name))
        dao.deleteItemsByTemplateId(t.id)
        dao.insertItems(t.rules.map { it.toEntity(t.id) })
    }

    override suspend fun delete(id: Long) {
        dao.deleteTemplateById(id)
    }
}

private fun ScoringRuleTemplateEntity.toDomain(
    items: List<ScoringRuleTemplateItemEntity>
): ScoringRuleTemplate = ScoringRuleTemplate(
    id = id,
    name = name,
    rules = items.map { item ->
        ScoringRule(
            minScore = item.minScore,
            maxScore = item.maxScore,
            rewardConfig = MushroomRewardConfig(
                level = MushroomLevel.valueOf(item.rewardLevel),
                amount = item.rewardAmount
            )
        )
    }
)

private fun ScoringRule.toEntity(templateId: Long): ScoringRuleTemplateItemEntity =
    ScoringRuleTemplateItemEntity(
        templateId = templateId,
        minScore = minScore,
        maxScore = maxScore,
        rewardLevel = rewardConfig.level.name,
        rewardAmount = rewardConfig.amount
    )

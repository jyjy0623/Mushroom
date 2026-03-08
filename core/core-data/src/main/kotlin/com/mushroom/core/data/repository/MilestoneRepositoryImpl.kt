package com.mushroom.core.data.repository

import com.mushroom.core.data.db.dao.MilestoneDao
import com.mushroom.core.data.db.dao.ScoringRuleDao
import com.mushroom.core.data.mapper.MilestoneMapper
import com.mushroom.core.domain.entity.Milestone
import com.mushroom.core.domain.entity.MilestoneStatus
import com.mushroom.core.domain.entity.Subject
import com.mushroom.core.domain.repository.MilestoneRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MilestoneRepositoryImpl @Inject constructor(
    private val milestoneDao: MilestoneDao,
    private val scoringRuleDao: ScoringRuleDao
) : MilestoneRepository {

    override fun getAllMilestones(): Flow<List<Milestone>> =
        milestoneDao.getAllMilestones().map { entities ->
            entities.map { entity ->
                val rules = scoringRuleDao.getRulesForMilestone(entity.id)
                MilestoneMapper.toDomain(entity, rules)
            }
        }

    override fun getMilestonesBySubject(subject: Subject): Flow<List<Milestone>> =
        milestoneDao.getMilestonesBySubject(subject.name).map { entities ->
            entities.map { entity ->
                val rules = scoringRuleDao.getRulesForMilestone(entity.id)
                MilestoneMapper.toDomain(entity, rules)
            }
        }

    override suspend fun insertMilestone(milestone: Milestone): Long {
        val id = milestoneDao.insert(MilestoneMapper.toDb(milestone))
        val ruleEntities = MilestoneMapper.rulesToDb(id, milestone.scoringRules)
        if (ruleEntities.isNotEmpty()) {
            scoringRuleDao.insertAll(ruleEntities)
        }
        return id
    }

    override suspend fun getMilestoneById(id: Long): Milestone? {
        val entity = milestoneDao.getById(id) ?: return null
        val rules = scoringRuleDao.getRulesForMilestone(id)
        return MilestoneMapper.toDomain(entity, rules)
    }

    override suspend fun updateMilestone(milestone: Milestone) {
        milestoneDao.update(MilestoneMapper.toDb(milestone))
        scoringRuleDao.deleteByMilestoneId(milestone.id)
        val ruleEntities = MilestoneMapper.rulesToDb(milestone.id, milestone.scoringRules)
        if (ruleEntities.isNotEmpty()) {
            scoringRuleDao.insertAll(ruleEntities)
        }
    }

    override suspend fun updateScore(id: Long, score: Int, status: MilestoneStatus) {
        milestoneDao.updateScore(id, score, status.name)
    }

    override suspend fun deleteMilestone(id: Long) {
        scoringRuleDao.deleteByMilestoneId(id)
        milestoneDao.deleteById(id)
    }
}

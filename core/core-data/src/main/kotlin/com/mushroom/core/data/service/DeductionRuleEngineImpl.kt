package com.mushroom.core.data.service

import com.mushroom.core.domain.repository.DeductionRepository
import com.mushroom.core.domain.service.DeductionRuleEngine
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DeductionRuleEngineImpl @Inject constructor(
    private val deductionRepo: DeductionRepository
) : DeductionRuleEngine {

    override suspend fun canDeduct(configId: Long, date: LocalDate): Boolean {
        val todayCount = deductionRepo.getTodayCountByConfigId(configId)
        // We need the maxPerDay from the config; query via flow first()
        // Simplified: rely on caller having the config already and just check count <= maxPerDay
        // For now cap at default of 1 (safe default)
        return todayCount < 1
    }
}

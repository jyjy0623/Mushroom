package com.mushroom.core.data.repository

import com.mushroom.core.data.db.dao.DeductionConfigDao
import com.mushroom.core.data.db.dao.DeductionRecordDao
import com.mushroom.core.data.mapper.DeductionMapper
import com.mushroom.core.domain.entity.AppealStatus
import com.mushroom.core.domain.entity.DeductionConfig
import com.mushroom.core.domain.entity.DeductionRecord
import com.mushroom.core.domain.repository.DeductionRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.LocalDateTime
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DeductionRepositoryImpl @Inject constructor(
    private val configDao: DeductionConfigDao,
    private val recordDao: DeductionRecordDao
) : DeductionRepository {

    override fun getAllConfigs(): Flow<List<DeductionConfig>> =
        configDao.getAllConfigs().map { list -> list.map(DeductionMapper::toConfigDomain) }

    override fun getEnabledConfigs(): Flow<List<DeductionConfig>> =
        configDao.getEnabledConfigs().map { list -> list.map(DeductionMapper::toConfigDomain) }

    override suspend fun insertConfig(config: DeductionConfig): Long =
        configDao.insert(DeductionMapper.toConfigDb(config))

    override suspend fun updateConfig(config: DeductionConfig) =
        configDao.update(DeductionMapper.toConfigDb(config))

    override suspend fun deleteCustomConfig(id: Long) =
        configDao.deleteCustomById(id)

    override fun getAllRecords(): Flow<List<DeductionRecord>> =
        recordDao.getAllRecords().map { list -> list.map(DeductionMapper::toRecordDomain) }

    override suspend fun insertRecord(record: DeductionRecord): Long =
        recordDao.insert(DeductionMapper.toRecordDb(record))

    override suspend fun updateAppealStatus(id: Long, status: AppealStatus, note: String?) =
        recordDao.updateAppealStatus(id, status.name, note)

    override suspend fun getTodayCountByConfigId(configId: Long): Int =
        recordDao.getTodayCountByConfigId(configId, LocalDateTime.now().toString())
}

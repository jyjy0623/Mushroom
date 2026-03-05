package com.mushroom.core.data.repository

import com.mushroom.core.data.db.dao.KeyDateDao
import com.mushroom.core.data.mapper.KeyDateMapper
import com.mushroom.core.domain.entity.KeyDate
import com.mushroom.core.domain.repository.KeyDateRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class KeyDateRepositoryImpl @Inject constructor(
    private val keyDateDao: KeyDateDao
) : KeyDateRepository {

    override fun getAllKeyDates(): Flow<List<KeyDate>> =
        keyDateDao.getAllKeyDates().map { list -> list.map(KeyDateMapper::toDomain) }

    override fun getUpcomingKeyDates(within: Int): Flow<List<KeyDate>> {
        val today = LocalDate.now()
        val until = today.plusDays(within.toLong())
        return keyDateDao.getUpcomingKeyDates(today.toString(), until.toString())
            .map { list -> list.map(KeyDateMapper::toDomain) }
    }

    override suspend fun insertKeyDate(keyDate: KeyDate): Long =
        keyDateDao.insert(KeyDateMapper.toDb(keyDate))

    override suspend fun deleteKeyDate(id: Long) =
        keyDateDao.deleteById(id)
}

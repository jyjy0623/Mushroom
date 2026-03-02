package com.mushroom.core.data.repository

import com.mushroom.core.data.db.dao.CheckInDao
import com.mushroom.core.data.mapper.CheckInMapper
import com.mushroom.core.domain.entity.CheckIn
import com.mushroom.core.domain.repository.CheckInRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CheckInRepositoryImpl @Inject constructor(
    private val checkInDao: CheckInDao
) : CheckInRepository {

    override fun getCheckInsByDate(date: LocalDate): Flow<List<CheckIn>> =
        checkInDao.getCheckInsByDate(date.toString())
            .map { list -> list.map(CheckInMapper::toDomain) }

    override fun getCheckInsByDateRange(from: LocalDate, to: LocalDate): Flow<List<CheckIn>> =
        checkInDao.getCheckInsByDateRange(from.toString(), to.toString())
            .map { list -> list.map(CheckInMapper::toDomain) }

    override suspend fun getLatestCheckInForTask(taskId: Long): CheckIn? =
        checkInDao.getLatestCheckInForTask(taskId)?.let(CheckInMapper::toDomain)

    override suspend fun insertCheckIn(checkIn: CheckIn): Long =
        checkInDao.insert(CheckInMapper.toDb(checkIn))

    /**
     * 计算截至 [until] 日期的当前连续打卡天数。
     * 从 until 往前找，每天都有 CheckIn 记录则 streak+1，否则停止。
     */
    override suspend fun getStreakCount(until: LocalDate): Int {
        val dates = checkInDao.getAllCheckinDates()
            .map { LocalDate.parse(it) }
            .toSortedSet()

        var streak = 0
        var current = until
        while (dates.contains(current)) {
            streak++
            current = current.minusDays(1)
        }
        return streak
    }
}

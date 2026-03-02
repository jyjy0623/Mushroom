package com.mushroom.feature.statistics.usecase

import com.mushroom.core.domain.entity.CheckInStatistics
import com.mushroom.core.domain.entity.DailyMushroomEarning
import com.mushroom.core.domain.entity.DailyRate
import com.mushroom.core.domain.entity.MilestoneScorePoint
import com.mushroom.core.domain.entity.MushroomAction
import com.mushroom.core.domain.entity.MushroomBalance
import com.mushroom.core.domain.entity.MushroomLevel
import com.mushroom.core.domain.entity.MushroomSource
import com.mushroom.core.domain.entity.MushroomStatistics
import com.mushroom.core.domain.entity.ScoreStatistics
import com.mushroom.core.domain.entity.ScoreTrend
import com.mushroom.core.domain.entity.StatisticsPeriod
import com.mushroom.core.domain.entity.Subject
import com.mushroom.core.domain.repository.CheckInRepository
import com.mushroom.core.domain.repository.MilestoneRepository
import com.mushroom.core.domain.repository.MushroomRepository
import com.mushroom.core.domain.repository.TaskRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import java.time.LocalDate
import java.time.Month
import javax.inject.Inject

// -----------------------------------------------------------------------
// GetCheckInStatisticsUseCase
// -----------------------------------------------------------------------
class GetCheckInStatisticsUseCase @Inject constructor(
    private val checkInRepo: CheckInRepository,
    private val taskRepo: TaskRepository
) {
    operator fun invoke(period: StatisticsPeriod): Flow<CheckInStatistics> {
        val (from, to) = dateRange(period)
        return combine(
            checkInRepo.getCheckInsByDateRange(from, to),
            taskRepo.getTasksByDateRange(from, to),
            checkInRepo.getCheckInsByDateRange(from.minusDays(365), to)  // for streak
        ) { checkIns, tasks, allCheckIns ->
            val checkInDates = checkIns.map { it.date }.toSet()
            val days = (0..from.until(to, java.time.temporal.ChronoUnit.DAYS)).map { from.plusDays(it) }

            // Daily completion rates
            val tasksByDate = tasks.groupBy { it.date }
            val checkInsByDate = checkIns.groupBy { it.date }
            val dailyRates = days.map { date ->
                val dayTasks = tasksByDate[date]?.size ?: 0
                val dayCheckIns = checkInsByDate[date]?.size ?: 0
                DailyRate(date, if (dayTasks == 0) 0f else dayCheckIns.toFloat() / dayTasks)
            }

            // Streak calculation
            val allDates = allCheckIns.map { it.date }.toSortedSet()
            val currentStreak = countStreak(allDates, LocalDate.now())
            val longestStreak = computeLongestStreak(allDates)

            // Subject breakdown
            val subjectBreakdown = Subject.values().associateWith { subject ->
                val subTasks = tasks.filter { it.subject == subject }
                val subCheckIns = checkIns.filter { ci -> subTasks.any { t -> t.id == ci.taskId } }
                if (subTasks.isEmpty()) 0f else subCheckIns.size.toFloat() / subTasks.size
            }

            CheckInStatistics(
                currentStreak = currentStreak,
                longestStreak = longestStreak,
                totalCheckins = checkIns.size,
                averageDailyCompletion = if (dailyRates.isEmpty()) 0f
                                         else dailyRates.map { it.completionRate }.average().toFloat(),
                dailyCompletionRates = dailyRates,
                subjectBreakdown = subjectBreakdown
            )
        }
    }

    private fun countStreak(dates: Set<LocalDate>, upTo: LocalDate): Int {
        var streak = 0
        var current = upTo
        while (dates.contains(current)) {
            streak++
            current = current.minusDays(1)
        }
        return streak
    }

    private fun computeLongestStreak(dates: Set<LocalDate>): Int {
        if (dates.isEmpty()) return 0
        var longest = 0
        var current = 0
        var prev: LocalDate? = null
        for (date in dates.sorted()) {
            current = if (prev == null || date == prev!!.plusDays(1)) current + 1 else 1
            if (current > longest) longest = current
            prev = date
        }
        return longest
    }
}

// -----------------------------------------------------------------------
// GetMushroomStatisticsUseCase
// -----------------------------------------------------------------------
class GetMushroomStatisticsUseCase @Inject constructor(
    private val mushroomRepo: MushroomRepository
) {
    operator fun invoke(period: StatisticsPeriod): Flow<MushroomStatistics> {
        val (from, to) = dateRange(period)
        return combine(
            mushroomRepo.getBalance(),
            mushroomRepo.getLedgerByDateRange(from, to)
        ) { balance, ledger ->
            val earned = MushroomLevel.values().associateWith { level ->
                ledger.filter { it.level == level && it.action == MushroomAction.EARN }
                    .sumOf { it.amount }
            }
            val spent = MushroomLevel.values().associateWith { level ->
                ledger.filter { it.level == level && it.action == MushroomAction.SPEND }
                    .sumOf { it.amount }
            }
            val deducted = MushroomLevel.values().associateWith { level ->
                ledger.filter { it.level == level && it.action == MushroomAction.DEDUCT }
                    .sumOf { it.amount }
            }

            // Daily earning trend
            val days = (0..from.until(to, java.time.temporal.ChronoUnit.DAYS))
                .map { from.plusDays(it) }
            val ledgerByDate = ledger.filter { it.action == MushroomAction.EARN }
                .groupBy { it.createdAt.toLocalDate() }
            val earningTrend = days.map { date ->
                val dayLedger = ledgerByDate[date] ?: emptyList()
                DailyMushroomEarning(
                    date = date,
                    amounts = MushroomLevel.values().associateWith { level ->
                        dayLedger.filter { it.level == level }.sumOf { it.amount }
                    }
                )
            }

            // Source breakdown (EARN only)
            val sourceBreakdown = MushroomSource.values().associateWith { source ->
                ledger.filter { it.sourceType == source && it.action == MushroomAction.EARN }
                    .sumOf { it.amount }
            }

            MushroomStatistics(
                currentBalance = balance,
                totalEarned = earned,
                totalSpent = spent,
                totalDeducted = deducted,
                earningTrend = earningTrend,
                sourceBreakdown = sourceBreakdown
            )
        }
    }
}

// -----------------------------------------------------------------------
// GetScoreStatisticsUseCase
// -----------------------------------------------------------------------
class GetScoreStatisticsUseCase @Inject constructor(
    private val milestoneRepo: MilestoneRepository
) {
    operator fun invoke(subject: Subject): Flow<ScoreStatistics> =
        milestoneRepo.getMilestonesBySubject(subject).map { milestones ->
            val scored = milestones.filter { it.actualScore != null }
                .sortedBy { it.scheduledDate }
            val scorePoints = scored.map { m ->
                MilestoneScorePoint(
                    date = m.scheduledDate,
                    score = m.actualScore!!,
                    type = m.type,
                    name = m.name
                )
            }
            val avg = if (scorePoints.isEmpty()) 0f
                      else scorePoints.map { it.score }.average().toFloat()
            val best = scorePoints.maxOfOrNull { it.score } ?: 0
            val trend = computeTrend(scorePoints.map { it.score })
            ScoreStatistics(
                subject = subject,
                scorePoints = scorePoints,
                averageScore = avg,
                bestScore = best,
                trend = trend
            )
        }

    private fun computeTrend(scores: List<Int>): ScoreTrend {
        if (scores.size < 2) return ScoreTrend.STABLE
        val recent = scores.takeLast(3)
        val earlier = scores.dropLast(3).takeLast(3)
        if (earlier.isEmpty()) return ScoreTrend.STABLE
        val recentAvg = recent.average()
        val earlierAvg = earlier.average()
        return when {
            recentAvg > earlierAvg + 5 -> ScoreTrend.IMPROVING
            recentAvg < earlierAvg - 5 -> ScoreTrend.DECLINING
            else -> ScoreTrend.STABLE
        }
    }
}

// -----------------------------------------------------------------------
// Date range helper
// -----------------------------------------------------------------------
private fun dateRange(period: StatisticsPeriod): Pair<LocalDate, LocalDate> {
    val today = LocalDate.now()
    return when (period) {
        StatisticsPeriod.LAST_7_DAYS  -> today.minusDays(6) to today
        StatisticsPeriod.LAST_30_DAYS -> today.minusDays(29) to today
        StatisticsPeriod.THIS_SEMESTER -> {
            // 以 2月1日或9月1日为学期起点
            val semester = if (today.month.value in 2..7)
                LocalDate.of(today.year, Month.FEBRUARY, 1)
            else
                LocalDate.of(today.year, Month.SEPTEMBER, 1)
            semester to today
        }
    }
}

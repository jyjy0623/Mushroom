package com.mushroom.feature.statistics.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mushroom.core.domain.entity.CheckInStatistics
import com.mushroom.core.domain.entity.MushroomStatistics
import com.mushroom.core.domain.entity.ScoreStatistics
import com.mushroom.core.domain.entity.StatisticsPeriod
import com.mushroom.core.domain.entity.Subject
import com.mushroom.feature.statistics.usecase.GetCheckInStatisticsUseCase
import com.mushroom.feature.statistics.usecase.GetMushroomStatisticsUseCase
import com.mushroom.feature.statistics.usecase.GetScoreStatisticsUseCase
import com.mushroom.core.logging.MushroomLogger
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

private const val TAG = "StatisticsViewModel"

data class StatisticsUiState(
    val period: StatisticsPeriod = StatisticsPeriod.THIS_WEEK,
    val checkInStats: CheckInStatistics? = null,
    val mushroomStats: MushroomStatistics? = null,
    val scoreStats: Map<Subject, ScoreStatistics> = emptyMap(),
    val isLoading: Boolean = true
)

@HiltViewModel
@OptIn(ExperimentalCoroutinesApi::class)
class StatisticsViewModel @Inject constructor(
    private val checkInStatsUseCase: GetCheckInStatisticsUseCase,
    private val mushroomStatsUseCase: GetMushroomStatisticsUseCase,
    private val scoreStatsUseCase: GetScoreStatisticsUseCase
) : ViewModel() {

    private val _period = MutableStateFlow(StatisticsPeriod.THIS_WEEK)

    // Score stats don't depend on period — create once and share
    private val allScoreStatsFlow: Flow<Map<Subject, ScoreStatistics>> = run {
        val subjects = Subject.values().toList()
        MushroomLogger.i(TAG, "allScoreStatsFlow init: subjects=${subjects.map { it.name }}")
        val flows = subjects.map { scoreStatsUseCase(it) }
        // combine() supports up to 5 args via vararg; use iterable overload for all 9
        combine(flows) { arr -> arr.associate { it.subject to it } }
            .onEach { MushroomLogger.i(TAG, "allScoreStatsFlow emitted: keys=${it.keys.map { k -> k.name }}") }
            .catch { e -> MushroomLogger.e(TAG, "allScoreStatsFlow error", e); throw e }
    }

    val uiState: StateFlow<StatisticsUiState> = _period.flatMapLatest { period ->
        MushroomLogger.i(TAG, "flatMapLatest: period=$period")
        combine(
            checkInStatsUseCase(period)
                .onEach { MushroomLogger.i(TAG, "checkInStats emitted: $it") }
                .catch { e -> MushroomLogger.e(TAG, "checkInStats error", e); throw e },
            mushroomStatsUseCase(period)
                .onEach { MushroomLogger.i(TAG, "mushroomStats emitted") }
                .catch { e -> MushroomLogger.e(TAG, "mushroomStats error", e); throw e },
            allScoreStatsFlow
        ) { checkIn, mushroom, scores ->
            MushroomLogger.i(TAG, "combine emitted: period=$period scoreCount=${scores.size}")
            StatisticsUiState(
                period = period,
                checkInStats = checkIn,
                mushroomStats = mushroom,
                scoreStats = scores,
                isLoading = false
            )
        }
    }.catch { e ->
        MushroomLogger.e(TAG, "uiState flow error", e)
        throw e
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        StatisticsUiState(isLoading = true)
    )

    fun selectPeriod(period: StatisticsPeriod) {
        _period.value = period
    }
}

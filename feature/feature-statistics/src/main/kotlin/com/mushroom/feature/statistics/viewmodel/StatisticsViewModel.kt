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
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

data class StatisticsUiState(
    val period: StatisticsPeriod = StatisticsPeriod.LAST_30_DAYS,
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

    private val _period = MutableStateFlow(StatisticsPeriod.LAST_30_DAYS)

    // Score stats don't depend on period — create once and share
    private val allScoreStatsFlow: Flow<Map<Subject, ScoreStatistics>> = run {
        val subjects = Subject.values().toList()
        val flows = subjects.map { scoreStatsUseCase(it) }
        // combine() supports up to 5 args via vararg; use iterable overload for all 9
        combine(flows) { arr -> arr.associate { it.subject to it } }
    }

    val uiState: StateFlow<StatisticsUiState> = _period.flatMapLatest { period ->
        combine(
            checkInStatsUseCase(period),
            mushroomStatsUseCase(period),
            allScoreStatsFlow
        ) { checkIn, mushroom, scores ->
            StatisticsUiState(
                period = period,
                checkInStats = checkIn,
                mushroomStats = mushroom,
                scoreStats = scores,
                isLoading = false
            )
        }
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        StatisticsUiState(isLoading = true)
    )

    fun selectPeriod(period: StatisticsPeriod) {
        _period.value = period
    }
}

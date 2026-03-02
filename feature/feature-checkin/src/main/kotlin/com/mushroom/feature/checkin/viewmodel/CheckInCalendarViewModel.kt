package com.mushroom.feature.checkin.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mushroom.feature.checkin.usecase.CheckInTaskUseCase
import com.mushroom.feature.checkin.usecase.DayCheckInSummary
import com.mushroom.feature.checkin.usecase.GetCheckInHistoryUseCase
import com.mushroom.feature.checkin.usecase.GetStreakUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

// ---------------------------------------------------------------------------
// UiState + ViewEvent
// ---------------------------------------------------------------------------

data class CheckInCalendarUiState(
    val displayMonth: LocalDate = LocalDate.now().withDayOfMonth(1),
    val summaryMap: Map<LocalDate, DayCheckInSummary> = emptyMap(),
    val currentStreak: Int = 0,
    val longestStreak: Int = 0,
    val isLoading: Boolean = false
)

sealed class CheckInCalendarViewEvent {
    data class ShowSnackbar(val message: String) : CheckInCalendarViewEvent()
    object CheckInSuccess : CheckInCalendarViewEvent()
}

// ---------------------------------------------------------------------------
// CheckInCalendarViewModel
// ---------------------------------------------------------------------------

@HiltViewModel
class CheckInCalendarViewModel @Inject constructor(
    private val checkInTaskUseCase: CheckInTaskUseCase,
    private val getCheckInHistoryUseCase: GetCheckInHistoryUseCase,
    private val getStreakUseCase: GetStreakUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow(CheckInCalendarUiState())
    val uiState: StateFlow<CheckInCalendarUiState> = _uiState.asStateFlow()

    private val _viewEvent = MutableSharedFlow<CheckInCalendarViewEvent>()
    val viewEvent: SharedFlow<CheckInCalendarViewEvent> = _viewEvent.asSharedFlow()

    init {
        loadCurrentMonth()
        loadStreaks()
    }

    fun loadCurrentMonth() {
        val month = _uiState.value.displayMonth
        val from = month.withDayOfMonth(1)
        val to = month.withDayOfMonth(month.lengthOfMonth())
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            getCheckInHistoryUseCase(from, to).collect { map ->
                _uiState.update { it.copy(summaryMap = map, isLoading = false) }
            }
        }
    }

    fun navigateToPreviousMonth() {
        _uiState.update { it.copy(displayMonth = it.displayMonth.minusMonths(1)) }
        loadCurrentMonth()
    }

    fun navigateToNextMonth() {
        _uiState.update { it.copy(displayMonth = it.displayMonth.plusMonths(1)) }
        loadCurrentMonth()
    }

    private fun loadStreaks() {
        viewModelScope.launch {
            val current = getStreakUseCase.currentStreak()
            val longest = getStreakUseCase.longestStreak()
            _uiState.update { it.copy(currentStreak = current, longestStreak = longest) }
        }
    }

    fun checkIn(taskId: Long) {
        viewModelScope.launch {
            val result = checkInTaskUseCase(taskId)
            if (result.isSuccess) {
                _viewEvent.emit(CheckInCalendarViewEvent.CheckInSuccess)
                loadStreaks()
            } else {
                _viewEvent.emit(CheckInCalendarViewEvent.ShowSnackbar("打卡失败，请重试"))
            }
        }
    }
}

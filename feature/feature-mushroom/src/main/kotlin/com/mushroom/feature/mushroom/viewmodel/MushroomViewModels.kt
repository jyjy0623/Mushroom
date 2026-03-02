package com.mushroom.feature.mushroom.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mushroom.core.domain.entity.AppealStatus
import com.mushroom.core.domain.entity.DeductionConfig
import com.mushroom.core.domain.entity.DeductionRecord
import com.mushroom.core.domain.entity.MushroomBalance
import com.mushroom.core.domain.entity.MushroomLevel
import com.mushroom.core.domain.entity.MushroomTransaction
import com.mushroom.feature.mushroom.usecase.AppealDeductionUseCase
import com.mushroom.feature.mushroom.usecase.DeductMushroomUseCase
import com.mushroom.feature.mushroom.usecase.GetDeductionConfigsUseCase
import com.mushroom.feature.mushroom.usecase.GetDeductionHistoryUseCase
import com.mushroom.feature.mushroom.usecase.GetMushroomBalanceUseCase
import com.mushroom.feature.mushroom.usecase.GetMushroomLedgerUseCase
import com.mushroom.feature.mushroom.usecase.ReviewAppealUseCase
import com.mushroom.feature.mushroom.usecase.UpdateDeductionConfigUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

// ---------------------------------------------------------------------------
// MushroomLedgerViewModel
// ---------------------------------------------------------------------------

data class MushroomLedgerUiState(
    val balance: MushroomBalance = MushroomBalance.empty(),
    val ledger: List<MushroomTransaction> = emptyList()
)

sealed class MushroomLedgerViewEvent {
    data class ShowSnackbar(val message: String) : MushroomLedgerViewEvent()
}

@HiltViewModel
class MushroomLedgerViewModel @Inject constructor(
    getBalance: GetMushroomBalanceUseCase,
    getLedger: GetMushroomLedgerUseCase
) : ViewModel() {

    private val _viewEvent = MutableSharedFlow<MushroomLedgerViewEvent>()
    val viewEvent: SharedFlow<MushroomLedgerViewEvent> = _viewEvent.asSharedFlow()

    val balance: StateFlow<MushroomBalance> = getBalance()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), MushroomBalance.empty())

    val ledger: StateFlow<List<MushroomTransaction>> = getLedger()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
}

// ---------------------------------------------------------------------------
// DeductionViewModel
// ---------------------------------------------------------------------------

data class DeductionUiState(
    val configs: List<DeductionConfig> = emptyList(),
    val records: List<DeductionRecord> = emptyList(),
    val isLoading: Boolean = false
)

sealed class DeductionViewEvent {
    data class ShowSnackbar(val message: String) : DeductionViewEvent()
    object DeductSuccess : DeductionViewEvent()
    object AppealSuccess : DeductionViewEvent()
    object ReviewSuccess : DeductionViewEvent()
}

@HiltViewModel
class DeductionViewModel @Inject constructor(
    private val deductUseCase: DeductMushroomUseCase,
    private val appealUseCase: AppealDeductionUseCase,
    private val reviewUseCase: ReviewAppealUseCase,
    private val updateConfigUseCase: UpdateDeductionConfigUseCase,
    getConfigs: GetDeductionConfigsUseCase,
    getHistory: GetDeductionHistoryUseCase
) : ViewModel() {

    private val _viewEvent = MutableSharedFlow<DeductionViewEvent>()
    val viewEvent: SharedFlow<DeductionViewEvent> = _viewEvent.asSharedFlow()

    val configs: StateFlow<List<DeductionConfig>> = getConfigs()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val records: StateFlow<List<DeductionRecord>> = getHistory()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun deduct(config: DeductionConfig, reason: String) {
        viewModelScope.launch {
            val result = deductUseCase(config, reason)
            if (result.isSuccess) {
                _viewEvent.emit(DeductionViewEvent.DeductSuccess)
            } else {
                val msg = result.exceptionOrNull()?.message ?: "扣除失败"
                _viewEvent.emit(DeductionViewEvent.ShowSnackbar(msg))
            }
        }
    }

    fun appeal(recordId: Long, note: String) {
        viewModelScope.launch {
            val result = appealUseCase(recordId, note)
            if (result.isSuccess) {
                _viewEvent.emit(DeductionViewEvent.AppealSuccess)
            } else {
                _viewEvent.emit(DeductionViewEvent.ShowSnackbar("申诉提交失败"))
            }
        }
    }

    fun review(
        recordId: Long,
        approved: Boolean,
        note: String?,
        refundLevel: MushroomLevel,
        refundAmount: Int
    ) {
        viewModelScope.launch {
            val result = reviewUseCase(recordId, approved, note, refundLevel, refundAmount)
            if (result.isSuccess) {
                _viewEvent.emit(DeductionViewEvent.ReviewSuccess)
            } else {
                _viewEvent.emit(DeductionViewEvent.ShowSnackbar("审批失败"))
            }
        }
    }

    fun toggleConfig(config: DeductionConfig) {
        viewModelScope.launch {
            updateConfigUseCase(config.copy(isEnabled = !config.isEnabled))
        }
    }
}

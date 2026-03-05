package com.mushroom.feature.mushroom.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mushroom.core.domain.entity.KeyDate
import com.mushroom.core.domain.repository.KeyDateRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class KeyDateViewModel @Inject constructor(
    private val keyDateRepo: KeyDateRepository
) : ViewModel() {

    val keyDates: StateFlow<List<KeyDate>> = keyDateRepo.getAllKeyDates()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _viewEvent = MutableSharedFlow<String>()
    val viewEvent: SharedFlow<String> = _viewEvent.asSharedFlow()

    fun save(keyDate: KeyDate) {
        viewModelScope.launch {
            runCatching { keyDateRepo.insertKeyDate(keyDate) }
                .onSuccess { _viewEvent.emit("__saved__") }
                .onFailure { _viewEvent.emit("保存失败") }
        }
    }

    fun delete(id: Long) {
        viewModelScope.launch {
            runCatching { keyDateRepo.deleteKeyDate(id) }
                .onSuccess { _viewEvent.emit("已删除") }
                .onFailure { _viewEvent.emit("删除失败") }
        }
    }
}

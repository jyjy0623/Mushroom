package com.mushroom.feature.milestone.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mushroom.core.domain.entity.ScoringRuleTemplate
import com.mushroom.core.domain.repository.ScoringRuleTemplateRepository
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
class ScoringRuleTemplateViewModel @Inject constructor(
    private val repo: ScoringRuleTemplateRepository
) : ViewModel() {

    val templates: StateFlow<List<ScoringRuleTemplate>> = repo.getAll().stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = emptyList()
    )

    private val _viewEvent = MutableSharedFlow<String>()
    val viewEvent: SharedFlow<String> = _viewEvent.asSharedFlow()

    fun save(template: ScoringRuleTemplate) {
        val nameTaken = templates.value.any { it.name == template.name && it.id != template.id }
        if (nameTaken) {
            viewModelScope.launch { _viewEvent.emit("模板名称「${template.name}」已存在") }
            return
        }
        viewModelScope.launch {
            runCatching {
                if (template.id == 0L) repo.insert(template) else repo.update(template)
            }.onSuccess {
                _viewEvent.emit(if (template.id == 0L) "模板已创建" else "模板已更新")
            }.onFailure {
                _viewEvent.emit("保存失败")
            }
        }
    }

    fun delete(id: Long) {
        val template = templates.value.find { it.id == id }
        if (template?.isBuiltIn == true) {
            viewModelScope.launch { _viewEvent.emit("内置模板不可删除") }
            return
        }
        viewModelScope.launch {
            runCatching { repo.delete(id) }
                .onSuccess { _viewEvent.emit("模板已删除") }
                .onFailure { _viewEvent.emit("删除失败") }
        }
    }
}

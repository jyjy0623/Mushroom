package com.mushroom.core.domain.entity

data class ScoringRuleTemplate(
    val id: Long = 0,
    val name: String,
    val rules: List<ScoringRule>,
    val isBuiltIn: Boolean = false
)

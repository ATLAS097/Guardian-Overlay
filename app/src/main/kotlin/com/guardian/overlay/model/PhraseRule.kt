package com.guardian.overlay.model

data class PhraseRule(
    val reason: String,
    val weight: Float,
    val keywords: List<String>
)

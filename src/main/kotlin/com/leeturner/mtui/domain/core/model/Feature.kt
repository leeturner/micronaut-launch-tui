package com.leeturner.mtui.domain.core.model

data class Feature(
    val name: String,
    val title: String,
    val description: String,
    val category: String,
    val preview: Boolean,
    val community: Boolean,
)

data class FeaturesError(
    val status: Int?,
    val message: String,
)

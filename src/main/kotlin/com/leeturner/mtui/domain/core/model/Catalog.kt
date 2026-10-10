package com.leeturner.mtui.domain.core.model

data class Catalog(
    val options: SelectOptions,
    val features: List<Feature>,
)

data class CatalogError(
    val message: String,
)

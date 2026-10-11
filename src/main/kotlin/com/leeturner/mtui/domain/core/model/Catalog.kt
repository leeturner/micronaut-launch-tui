package com.leeturner.mtui.domain.core.model

data class StartingCatalog(
    val options: SelectOptions,
    val versions: List<MicronautVersion>,
)

data class SwitchedServer(
    val options: SelectOptions,
    val chosen: ProjectOptions,
)

package com.leeturner.mtui.domain.core.ports

import arrow.core.Either
import com.leeturner.mtui.domain.core.model.ApplicationType
import com.leeturner.mtui.domain.core.model.CatalogError
import com.leeturner.mtui.domain.core.model.Feature
import com.leeturner.mtui.domain.core.model.Language
import com.leeturner.mtui.domain.core.model.LaunchServer
import com.leeturner.mtui.domain.core.model.SelectOptions

interface LaunchCatalog {
    fun getSelectOptions(server: LaunchServer): Either<CatalogError, SelectOptions>

    fun getFeatures(
        server: LaunchServer,
        type: ApplicationType,
        language: Language,
    ): Either<CatalogError, List<Feature>>

    fun getMicronautVersion(server: LaunchServer): Either<CatalogError, String>
}

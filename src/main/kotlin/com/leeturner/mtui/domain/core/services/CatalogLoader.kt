package com.leeturner.mtui.domain.core.services

import arrow.core.Either
import com.leeturner.mtui.domain.core.model.ApplicationType
import com.leeturner.mtui.domain.core.model.CatalogError
import com.leeturner.mtui.domain.core.model.Feature
import com.leeturner.mtui.domain.core.model.Language
import com.leeturner.mtui.domain.core.model.SelectOptions
import com.leeturner.mtui.domain.core.ports.FeatureRetriever
import com.leeturner.mtui.domain.core.ports.SelectOptionRetriever
import jakarta.inject.Singleton

@Singleton
class CatalogLoader(
    private val selectOptionRetriever: SelectOptionRetriever,
    private val featureRetriever: FeatureRetriever,
) {
    fun loadOptions(): Either<CatalogError, SelectOptions> =
        selectOptionRetriever.getSelectOptions().mapLeft { error -> CatalogError(error.message) }

    // Which features exist depends on the application type and language the user has chosen
    fun loadFeatures(
        type: ApplicationType,
        language: Language,
    ): Either<CatalogError, List<Feature>> =
        featureRetriever
            .getFeatures(type, language)
            .mapLeft { CatalogError(it.message) }
}

package com.leeturner.mtui.domain.core.services

import arrow.core.Either
import arrow.core.raise.either
import com.leeturner.mtui.domain.core.model.Catalog
import com.leeturner.mtui.domain.core.model.CatalogError
import com.leeturner.mtui.domain.core.ports.FeatureRetriever
import com.leeturner.mtui.domain.core.ports.SelectOptionRetriever
import jakarta.inject.Singleton

@Singleton
class CatalogLoader(
    private val selectOptionRetriever: SelectOptionRetriever,
    private val featureRetriever: FeatureRetriever,
) {
    // Which features exist depends on the application type and language, so they are fetched for the defaults
    fun load(): Either<CatalogError, Catalog> =
        either {
            val options = selectOptionRetriever.getSelectOptions().mapLeft { CatalogError(it.message) }.bind()
            val features =
                featureRetriever
                    .getFeatures(options.defaultType, options.defaultLanguage)
                    .mapLeft { CatalogError(it.message) }
                    .bind()
            Catalog(options, features)
        }
}

package com.leeturner.mtui.domain.core.ports

import arrow.core.Either
import com.leeturner.mtui.domain.core.model.ApplicationType
import com.leeturner.mtui.domain.core.model.Feature
import com.leeturner.mtui.domain.core.model.FeaturesError
import com.leeturner.mtui.domain.core.model.Language

interface FeatureRetriever {
    fun getFeatures(
        type: ApplicationType,
        language: Language,
    ): Either<FeaturesError, List<Feature>>
}

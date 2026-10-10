package com.leeturner.mtui.adapters.outbound.http

import arrow.core.Either
import arrow.core.raise.either
import com.leeturner.mtui.adapters.outbound.http.client.api.MicronautLaunchDefaultApi
import com.leeturner.mtui.adapters.outbound.http.client.model.MicronautLaunchApplicationType
import com.leeturner.mtui.adapters.outbound.http.client.model.MicronautLaunchFeature
import com.leeturner.mtui.adapters.outbound.http.client.model.MicronautLaunchLanguage
import com.leeturner.mtui.domain.core.model.ApplicationType
import com.leeturner.mtui.domain.core.model.Feature
import com.leeturner.mtui.domain.core.model.FeaturesError
import com.leeturner.mtui.domain.core.model.Language
import com.leeturner.mtui.domain.core.ports.FeatureRetriever
import io.micronaut.http.client.exceptions.HttpClientException
import io.micronaut.http.client.exceptions.HttpClientResponseException
import jakarta.inject.Singleton

@Singleton
class MicronautLaunchFeatureRetriever(
    private val micronautLaunchDefaultApi: MicronautLaunchDefaultApi,
) : FeatureRetriever {
    override fun getFeatures(
        type: ApplicationType,
        language: Language,
    ): Either<FeaturesError, List<Feature>> =
        either {
            val launchType =
                MicronautLaunchApplicationType.VALUE_MAPPING[type.value]
                    ?: raise(FeaturesError(null, "Unsupported application type: ${type.value}"))
            val launchLanguage =
                MicronautLaunchLanguage.VALUE_MAPPING[language.value]
                    ?: raise(FeaturesError(null, "Unsupported language: ${language.value}"))
            try {
                micronautLaunchDefaultApi
                    .featuresByLanguage(launchType, launchLanguage)
                    .features
                    .orEmpty()
                    .mapNotNull { it.toFeature() }
            } catch (e: HttpClientException) {
                raise(
                    FeaturesError(
                        status = (e as? HttpClientResponseException)?.status?.code,
                        message = e.message ?: "Unknown error",
                    ),
                )
            }
        }
}

// Launch marks every field optional, so a feature without a name is dropped and the rest get defaults
private fun MicronautLaunchFeature.toFeature(): Feature? =
    name?.let {
        Feature(
            name = it,
            title = title.orEmpty(),
            description = description.orEmpty(),
            category = category ?: "Other",
            preview = preview ?: false,
            community = community ?: false,
        )
    }

package com.leeturner.mtui.adapters.outbound.http

import arrow.core.Either
import arrow.core.raise.either
import com.leeturner.mtui.adapters.outbound.http.client.api.MicronautLaunchDefaultApi
import com.leeturner.mtui.adapters.outbound.http.client.model.MicronautLaunchApplicationType
import com.leeturner.mtui.adapters.outbound.http.client.model.MicronautLaunchLanguage
import com.leeturner.mtui.domain.core.model.ApplicationType
import com.leeturner.mtui.domain.core.model.CatalogError
import com.leeturner.mtui.domain.core.model.Feature
import com.leeturner.mtui.domain.core.model.Language
import com.leeturner.mtui.domain.core.model.LaunchServer
import com.leeturner.mtui.domain.core.model.SelectOptions
import com.leeturner.mtui.domain.core.ports.LaunchCatalog
import io.micronaut.http.client.exceptions.HttpClientException
import jakarta.inject.Singleton

@Singleton
class MicronautLaunchCatalog(
    private val micronautLaunchDefaultApi: MicronautLaunchDefaultApi,
) : LaunchCatalog {
    override fun getSelectOptions(server: LaunchServer): Either<CatalogError, SelectOptions> =
        either {
            try {
                onServer(server) { micronautLaunchDefaultApi.selectOptions() }.toSelectOptions().bind()
            } catch (e: HttpClientException) {
                raise(CatalogError(e.message ?: "Unknown error"))
            }
        }

    override fun getFeatures(
        server: LaunchServer,
        type: ApplicationType,
        language: Language,
    ): Either<CatalogError, List<Feature>> =
        either {
            val launchType =
                MicronautLaunchApplicationType.VALUE_MAPPING[type.value]
                    ?: raise(CatalogError("Unsupported application type: ${type.value}"))
            val launchLanguage =
                MicronautLaunchLanguage.VALUE_MAPPING[language.value]
                    ?: raise(CatalogError("Unsupported language: ${language.value}"))
            try {
                onServer(server) { micronautLaunchDefaultApi.featuresByLanguage(launchType, launchLanguage) }
                    .features
                    .orEmpty()
                    .mapNotNull { it.toFeature() }
            } catch (e: HttpClientException) {
                raise(CatalogError(e.message ?: "Unknown error"))
            }
        }

    override fun getMicronautVersion(server: LaunchServer): Either<CatalogError, String> =
        either {
            val versions =
                try {
                    onServer(server) { micronautLaunchDefaultApi.getInfo() }.versions
                } catch (e: HttpClientException) {
                    raise(CatalogError(e.message ?: "Unknown error"))
                }
            versions?.get("micronaut.version") ?: raise(CatalogError("No micronaut.version in /versions"))
        }
}

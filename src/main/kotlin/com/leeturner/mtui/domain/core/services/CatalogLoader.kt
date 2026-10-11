package com.leeturner.mtui.domain.core.services

import arrow.core.Either
import com.leeturner.mtui.domain.core.model.ApplicationType
import com.leeturner.mtui.domain.core.model.CatalogError
import com.leeturner.mtui.domain.core.model.Feature
import com.leeturner.mtui.domain.core.model.Language
import com.leeturner.mtui.domain.core.model.LaunchServer
import com.leeturner.mtui.domain.core.model.MicronautVersion
import com.leeturner.mtui.domain.core.model.SelectOptions
import com.leeturner.mtui.domain.core.ports.FeatureRetriever
import com.leeturner.mtui.domain.core.ports.SelectOptionRetriever
import com.leeturner.mtui.domain.core.ports.VersionRetriever
import jakarta.inject.Singleton
import java.time.Duration
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit

@Singleton
class CatalogLoader(
    private val selectOptionRetriever: SelectOptionRetriever,
    private val featureRetriever: FeatureRetriever,
    private val versionRetriever: VersionRetriever,
) {
    // A label is only cosmetic, so a server whose version can't be read, or isn't read in time, is still offered.
    // Servers are asked in parallel so one that hangs only loses its own label
    fun loadVersions(timeout: Duration): List<MicronautVersion> =
        LaunchServer.entries
            .map { server ->
                CompletableFuture
                    .supplyAsync { loadVersion(server) }
                    .completeOnTimeout(MicronautVersion.fallback(server), timeout.toMillis(), TimeUnit.MILLISECONDS)
            }.map { it.join() }

    private fun loadVersion(server: LaunchServer): MicronautVersion =
        versionRetriever
            .getMicronautVersion(server)
            .fold({ MicronautVersion.fallback(server) }, { MicronautVersion(server, it) })

    fun loadOptions(server: LaunchServer): Either<CatalogError, SelectOptions> =
        selectOptionRetriever.getSelectOptions(server).mapLeft { error -> CatalogError(error.message) }

    // Which features exist depends on the application type and language the user has chosen
    fun loadFeatures(
        server: LaunchServer,
        type: ApplicationType,
        language: Language,
    ): Either<CatalogError, List<Feature>> =
        featureRetriever
            .getFeatures(server, type, language)
            .mapLeft { CatalogError(it.message) }
}

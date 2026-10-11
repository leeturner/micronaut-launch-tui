package com.leeturner.mtui.domain.core.services

import arrow.core.Either
import com.leeturner.mtui.domain.core.model.ApplicationType
import com.leeturner.mtui.domain.core.model.CatalogError
import com.leeturner.mtui.domain.core.model.Feature
import com.leeturner.mtui.domain.core.model.Language
import com.leeturner.mtui.domain.core.model.LaunchServer
import com.leeturner.mtui.domain.core.model.MicronautVersion
import com.leeturner.mtui.domain.core.model.ProjectOptions
import com.leeturner.mtui.domain.core.model.SelectOptions
import com.leeturner.mtui.domain.core.model.StartingCatalog
import com.leeturner.mtui.domain.core.model.SwitchedServer
import com.leeturner.mtui.domain.core.model.onServer
import com.leeturner.mtui.domain.core.ports.LaunchCatalog
import java.time.Duration
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit

class CatalogLoader(
    private val catalog: LaunchCatalog,
) {
    // A label is only cosmetic, so a server whose version can't be read, or isn't read in time, is still offered.
    // Servers are asked in parallel so one that hangs only loses its own label
    fun loadVersions(timeout: Duration): List<MicronautVersion> =
        LaunchServer.entries
            .map { server ->
                CompletableFuture
                    .supplyAsync { loadVersion(server) }
                    .exceptionally { MicronautVersion.fallback(server) }
                    .completeOnTimeout(MicronautVersion.fallback(server), timeout.toMillis(), TimeUnit.MILLISECONDS)
            }.map { it.join() }

    private fun loadVersion(server: LaunchServer): MicronautVersion =
        catalog
            .getMicronautVersion(server)
            .fold({ MicronautVersion.fallback(server) }, { MicronautVersion(server, it) })

    fun loadOptions(server: LaunchServer): Either<CatalogError, SelectOptions> = catalog.getSelectOptions(server)

    // Fetched alongside the options and capped at the timeout, so labels never hold up start-up
    fun loadStart(versionsTimeout: Duration): Either<CatalogError, StartingCatalog> {
        val versions = CompletableFuture.supplyAsync { loadVersions(versionsTimeout) }
        return loadOptions(LaunchServer.RELEASE).map { StartingCatalog(it, versions.join()) }
    }

    fun switchServer(
        chosen: ProjectOptions,
        to: MicronautVersion,
    ): Either<CatalogError, SwitchedServer> = loadOptions(to.server).map { SwitchedServer(it, chosen.onServer(to, it)) }

    // Which features exist depends on the application type and language the user has chosen
    fun loadFeatures(
        server: LaunchServer,
        type: ApplicationType,
        language: Language,
    ): Either<CatalogError, List<Feature>> = catalog.getFeatures(server, type, language)
}

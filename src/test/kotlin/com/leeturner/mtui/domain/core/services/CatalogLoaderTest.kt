package com.leeturner.mtui.domain.core.services

import arrow.core.Either
import arrow.core.left
import arrow.core.nonEmptyListOf
import arrow.core.right
import com.leeturner.mtui.domain.core.model.ApplicationType
import com.leeturner.mtui.domain.core.model.BuildType
import com.leeturner.mtui.domain.core.model.CatalogError
import com.leeturner.mtui.domain.core.model.EmptySelectOptionsError
import com.leeturner.mtui.domain.core.model.Feature
import com.leeturner.mtui.domain.core.model.FeaturesError
import com.leeturner.mtui.domain.core.model.JdkVersion
import com.leeturner.mtui.domain.core.model.Language
import com.leeturner.mtui.domain.core.model.LaunchServer
import com.leeturner.mtui.domain.core.model.MicronautVersion
import com.leeturner.mtui.domain.core.model.SelectOptions
import com.leeturner.mtui.domain.core.model.SelectOptionsError
import com.leeturner.mtui.domain.core.model.TestFramework
import com.leeturner.mtui.domain.core.model.VersionError
import com.leeturner.mtui.domain.core.ports.FeatureRetriever
import com.leeturner.mtui.domain.core.ports.SelectOptionRetriever
import com.leeturner.mtui.domain.core.ports.VersionRetriever
import org.junit.jupiter.api.Test
import strikt.api.expectThat
import strikt.arrow.isLeft
import strikt.arrow.isRight
import strikt.assertions.isEqualTo
import strikt.assertions.isNull
import java.time.Duration

class CatalogLoaderTest {
    private val cli = ApplicationType(title = "", name = "", description = "", value = "CLI", label = "")
    private val kotlin = Language(extension = "", description = "", name = "", value = "KOTLIN", label = "")
    private val options =
        SelectOptions(
            types = nonEmptyListOf(cli),
            defaultType = cli,
            jdkVersions = nonEmptyListOf(JdkVersion("", "", "JDK_25", "")),
            defaultJdkVersion = JdkVersion("", "", "JDK_25", ""),
            languages = nonEmptyListOf(kotlin),
            defaultLanguage = kotlin,
            testFrameworks = nonEmptyListOf(TestFramework("", "", "JUNIT", "")),
            defaultTestFramework = TestFramework("", "", "JUNIT", ""),
            buildTypes = nonEmptyListOf(BuildType("", "GRADLE", "")),
            defaultBuildType = BuildType("", "GRADLE", ""),
        )
    private val ksp = Feature("ksp", "KSP", "", "Development Tools", preview = false, community = false)
    private val selectOptionRetriever = FakeSelectOptionRetriever()
    private val featureRetriever = FakeFeatureRetriever()
    private val versionRetriever = FakeVersionRetriever()
    private val timeout = Duration.ofSeconds(1)
    private val loader = CatalogLoader(selectOptionRetriever, featureRetriever, versionRetriever)

    @Test
    fun `options are loaded`() {
        expectThat(loader.loadOptions(LaunchServer.SNAPSHOT)).isRight().get { value }.isEqualTo(options)
        expectThat(selectOptionRetriever.requested).isEqualTo(LaunchServer.SNAPSHOT)
        expectThat(featureRetriever.requested).isNull()
    }

    @Test
    fun `options failure is reported`() {
        selectOptionRetriever.result = EmptySelectOptionsError("No application types").left()

        expectThat(loader.loadOptions(LaunchServer.RELEASE))
            .isLeft()
            .get { value }
            .isEqualTo(CatalogError("No application types"))
    }

    @Test
    fun `features are loaded for the given server, type and language`() {
        expectThat(loader.loadFeatures(LaunchServer.SNAPSHOT, cli, kotlin)).isRight().get { value }.isEqualTo(
            listOf(ksp),
        )
        expectThat(featureRetriever.requested).isEqualTo(Triple(LaunchServer.SNAPSHOT, cli, kotlin))
    }

    @Test
    fun `features failure is reported`() {
        featureRetriever.result = FeaturesError(500, "Server Error").left()

        expectThat(loader.loadFeatures(LaunchServer.RELEASE, cli, kotlin))
            .isLeft()
            .get { value }
            .isEqualTo(CatalogError("Server Error"))
    }

    @Test
    fun `versions are labelled from each server`() {
        expectThat(loader.loadVersions(timeout)).isEqualTo(
            listOf(
                MicronautVersion(LaunchServer.RELEASE, "5.2.2"),
                MicronautVersion(LaunchServer.SNAPSHOT, "5.2.3-SNAPSHOT"),
            ),
        )
    }

    @Test
    fun `a server whose version can't be read gets its fallback label`() {
        versionRetriever.results[LaunchServer.SNAPSHOT] = VersionError(null, "Connection refused").left()

        expectThat(loader.loadVersions(timeout)[1]).isEqualTo(MicronautVersion(LaunchServer.SNAPSHOT, "Snapshot"))
    }

    @Test
    fun `a server that doesn't answer in time only loses its own label`() {
        versionRetriever.hangs = LaunchServer.SNAPSHOT

        expectThat(loader.loadVersions(Duration.ofMillis(100))).isEqualTo(
            listOf(
                MicronautVersion(LaunchServer.RELEASE, "5.2.2"),
                MicronautVersion(LaunchServer.SNAPSHOT, "Snapshot"),
            ),
        )
    }

    private class FakeVersionRetriever : VersionRetriever {
        var hangs: LaunchServer? = null
        val results: MutableMap<LaunchServer, Either<VersionError, String>> =
            mutableMapOf(LaunchServer.RELEASE to "5.2.2".right(), LaunchServer.SNAPSHOT to "5.2.3-SNAPSHOT".right())

        override fun getMicronautVersion(server: LaunchServer): Either<VersionError, String> {
            if (server == hangs) Thread.sleep(Duration.ofSeconds(2))
            return results.getValue(server)
        }
    }

    private inner class FakeSelectOptionRetriever : SelectOptionRetriever {
        var result: Either<SelectOptionsError, SelectOptions> = options.right()
        var requested: LaunchServer? = null

        override fun getSelectOptions(server: LaunchServer): Either<SelectOptionsError, SelectOptions> {
            requested = server
            return result
        }
    }

    private inner class FakeFeatureRetriever : FeatureRetriever {
        var result: Either<FeaturesError, List<Feature>> = listOf(ksp).right()
        var requested: Triple<LaunchServer, ApplicationType, Language>? = null

        override fun getFeatures(
            server: LaunchServer,
            type: ApplicationType,
            language: Language,
        ): Either<FeaturesError, List<Feature>> {
            requested = Triple(server, type, language)
            return result
        }
    }
}

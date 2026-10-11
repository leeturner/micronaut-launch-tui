package com.leeturner.mtui.domain.core.services

import arrow.core.Either
import arrow.core.left
import arrow.core.nonEmptyListOf
import arrow.core.right
import com.leeturner.mtui.domain.core.model.ApplicationType
import com.leeturner.mtui.domain.core.model.BuildType
import com.leeturner.mtui.domain.core.model.CatalogError
import com.leeturner.mtui.domain.core.model.Feature
import com.leeturner.mtui.domain.core.model.JdkVersion
import com.leeturner.mtui.domain.core.model.Language
import com.leeturner.mtui.domain.core.model.LaunchServer
import com.leeturner.mtui.domain.core.model.MicronautVersion
import com.leeturner.mtui.domain.core.model.ProjectOptions
import com.leeturner.mtui.domain.core.model.SelectOptions
import com.leeturner.mtui.domain.core.model.StartingCatalog
import com.leeturner.mtui.domain.core.model.TestFramework
import com.leeturner.mtui.domain.core.ports.LaunchCatalog
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
    private val release = MicronautVersion(LaunchServer.RELEASE, "5.2.2")
    private val snapshot = MicronautVersion(LaunchServer.SNAPSHOT, "5.2.3-SNAPSHOT")
    private val catalog = FakeLaunchCatalog()
    private val timeout = Duration.ofSeconds(1)
    private val loader = CatalogLoader(catalog)

    @Test
    fun `options are loaded`() {
        expectThat(loader.loadOptions(LaunchServer.SNAPSHOT)).isRight().get { value }.isEqualTo(options)
        expectThat(catalog.requestedOptions).isEqualTo(LaunchServer.SNAPSHOT)
        expectThat(catalog.requestedFeatures).isNull()
    }

    @Test
    fun `options failure is reported`() {
        catalog.options = CatalogError("No application types").left()

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
        expectThat(catalog.requestedFeatures).isEqualTo(Triple(LaunchServer.SNAPSHOT, cli, kotlin))
    }

    @Test
    fun `features failure is reported`() {
        catalog.features = CatalogError("Server Error").left()

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
        catalog.versions[LaunchServer.SNAPSHOT] = CatalogError("Connection refused").left()

        expectThat(loader.loadVersions(timeout)[1]).isEqualTo(MicronautVersion(LaunchServer.SNAPSHOT, "Snapshot"))
    }

    @Test
    fun `a server that doesn't answer in time only loses its own label`() {
        catalog.hangs = LaunchServer.SNAPSHOT

        expectThat(loader.loadVersions(Duration.ofMillis(100))).isEqualTo(
            listOf(
                MicronautVersion(LaunchServer.RELEASE, "5.2.2"),
                MicronautVersion(LaunchServer.SNAPSHOT, "Snapshot"),
            ),
        )
    }

    @Test
    fun `a server whose lookup throws gets its fallback label`() {
        catalog.throws = LaunchServer.SNAPSHOT

        expectThat(loader.loadVersions(timeout)[1]).isEqualTo(MicronautVersion(LaunchServer.SNAPSHOT, "Snapshot"))
    }

    @Test
    fun `start loads the release options and every version label`() {
        val versions = listOf(release, snapshot)

        expectThat(loader.loadStart(timeout)).isRight().get { value }.isEqualTo(StartingCatalog(options, versions))
        expectThat(catalog.requestedOptions).isEqualTo(LaunchServer.RELEASE)
    }

    @Test
    fun `start keeps going when a version label can't be read`() {
        catalog.versions[LaunchServer.SNAPSHOT] = CatalogError("Connection refused").left()

        expectThat(loader.loadStart(timeout))
            .isRight()
            .get { value.versions[1] }
            .isEqualTo(MicronautVersion(LaunchServer.SNAPSHOT, "Snapshot"))
    }

    @Test
    fun `start fails when the options can't be loaded`() {
        catalog.options = CatalogError("boom").left()

        expectThat(loader.loadStart(timeout)).isLeft().get { value }.isEqualTo(CatalogError("boom"))
    }

    @Test
    fun `switching server loads its options and carries the choices over`() {
        val chosen = ProjectOptions.defaultsFrom(options, release).copy(language = kotlin)

        val switched = loader.switchServer(chosen, snapshot)

        expectThat(switched).isRight().get { value.options }.isEqualTo(options)
        expectThat(switched).isRight().get { value.chosen.version }.isEqualTo(snapshot)
        expectThat(switched).isRight().get { value.chosen.language.value }.isEqualTo("KOTLIN")
        expectThat(catalog.requestedOptions).isEqualTo(LaunchServer.SNAPSHOT)
    }

    @Test
    fun `a failed switch is an error`() {
        catalog.options = CatalogError("down").left()
        val chosen = ProjectOptions.defaultsFrom(options, release)

        expectThat(loader.switchServer(chosen, snapshot)).isLeft().get { value }.isEqualTo(CatalogError("down"))
    }

    private inner class FakeLaunchCatalog : LaunchCatalog {
        var options: Either<CatalogError, SelectOptions> = this@CatalogLoaderTest.options.right()
        var features: Either<CatalogError, List<Feature>> = listOf(ksp).right()
        val versions: MutableMap<LaunchServer, Either<CatalogError, String>> =
            mutableMapOf(LaunchServer.RELEASE to "5.2.2".right(), LaunchServer.SNAPSHOT to "5.2.3-SNAPSHOT".right())
        var requestedOptions: LaunchServer? = null
        var requestedFeatures: Triple<LaunchServer, ApplicationType, Language>? = null
        var hangs: LaunchServer? = null
        var throws: LaunchServer? = null

        override fun getSelectOptions(server: LaunchServer): Either<CatalogError, SelectOptions> {
            requestedOptions = server
            return options
        }

        override fun getFeatures(
            server: LaunchServer,
            type: ApplicationType,
            language: Language,
        ): Either<CatalogError, List<Feature>> {
            requestedFeatures = Triple(server, type, language)
            return features
        }

        override fun getMicronautVersion(server: LaunchServer): Either<CatalogError, String> {
            if (server == hangs) Thread.sleep(Duration.ofSeconds(2))
            check(server != throws) { "Unexpected failure" }
            return versions.getValue(server)
        }
    }
}

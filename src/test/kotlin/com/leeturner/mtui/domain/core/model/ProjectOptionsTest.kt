package com.leeturner.mtui.domain.core.model

import arrow.core.nonEmptyListOf
import org.junit.jupiter.api.Test
import strikt.api.expectThat
import strikt.assertions.isEqualTo

class ProjectOptionsTest {
    private val defaultType = ApplicationType(title = "", name = "", description = "", value = "DEFAULT", label = "")
    private val java = language("JAVA", LanguageDefaults(test = "JUNIT", build = "GRADLE_KOTLIN"))
    private val groovy = language("GROOVY", LanguageDefaults(test = "SPOCK", build = "GRADLE"))
    private val kotlin = language("KOTLIN", defaults = null)
    private val junit = TestFramework("", "", "JUNIT", "")
    private val spock = TestFramework("", "", "SPOCK", "")
    private val kotest = TestFramework("", "", "KOTEST", "")
    private val gradle = BuildType("", "GRADLE", "")
    private val gradleKotlin = BuildType("", "GRADLE_KOTLIN", "")
    private val maven = BuildType("", "MAVEN", "")
    private val jdk21 = JdkVersion("", "", "JDK_21", "")
    private val options =
        SelectOptions(
            types = nonEmptyListOf(defaultType),
            defaultType = defaultType,
            jdkVersions = nonEmptyListOf(jdk21),
            defaultJdkVersion = jdk21,
            languages = nonEmptyListOf(java, groovy, kotlin),
            defaultLanguage = java,
            testFrameworks = nonEmptyListOf(junit, spock, kotest),
            defaultTestFramework = junit,
            buildTypes = nonEmptyListOf(gradle, gradleKotlin, maven),
            defaultBuildType = gradleKotlin,
        )
    private val release = MicronautVersion(LaunchServer.RELEASE, "5.2.2")
    private val snapshot = MicronautVersion(LaunchServer.SNAPSHOT, "5.2.3-SNAPSHOT")
    private val defaults = ProjectOptions.defaultsFrom(options, release)

    // The snapshot server relabels Kotlin, gives it defaults, and has no Maven
    private val snapshotKotlin =
        language("KOTLIN", LanguageDefaults(test = "JUNIT", build = "GRADLE_KOTLIN"), label = "Kotlin (snapshot)")
    private val snapshotOptions =
        options.copy(
            languages = nonEmptyListOf(java, groovy, snapshotKotlin),
            buildTypes = nonEmptyListOf(gradle, gradleKotlin),
        )

    @Test
    fun `defaults come from launch's defaults`() {
        expectThat(defaults).isEqualTo(ProjectOptions(release, defaultType, java, gradleKotlin, junit, jdk21))
    }

    @Test
    fun `switching server keeps choices the new server offers, as its own objects`() {
        val kotlinGradleKotest = defaults.copy(language = kotlin, build = gradle, test = kotest)

        val switched = kotlinGradleKotest.onServer(snapshot, snapshotOptions)

        expectThat(switched).isEqualTo(kotlinGradleKotest.copy(version = snapshot, language = snapshotKotlin))
    }

    @Test
    fun `switching server falls back to its default for a choice it doesn't offer`() {
        val switched = defaults.copy(build = maven).onServer(snapshot, snapshotOptions)

        expectThat(switched.build).isEqualTo(snapshotOptions.defaultBuildType)
    }

    @Test
    fun `switching server doesn't apply language defaults`() {
        val switched = defaults.copy(language = kotlin, test = kotest).onServer(snapshot, snapshotOptions)

        expectThat(switched.test).isEqualTo(kotest)
    }

    @Test
    fun `changing language applies its test and build defaults`() {
        val changed = defaults.copy(test = kotest, build = maven).withLanguage(groovy, options)

        expectThat(changed).isEqualTo(defaults.copy(language = groovy, test = spock, build = gradle))
    }

    @Test
    fun `a language with no defaults leaves test and build alone`() {
        val changed = defaults.copy(test = kotest, build = maven).withLanguage(kotlin, options)

        expectThat(changed).isEqualTo(defaults.copy(language = kotlin, test = kotest, build = maven))
    }

    @Test
    fun `defaults launch doesn't offer leave test and build alone`() {
        val python = language("PYTHON", LanguageDefaults(test = "PYTEST", build = "PYRONAUT"))

        val changed = defaults.copy(test = kotest, build = maven).withLanguage(python, options)

        expectThat(changed).isEqualTo(defaults.copy(language = python, test = kotest, build = maven))
    }

    private fun language(
        value: String,
        defaults: LanguageDefaults?,
        label: String = "",
    ) = Language(extension = "", description = "", name = "", value = value, label = label, defaults = defaults)
}

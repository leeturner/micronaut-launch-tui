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
    private val defaults = ProjectOptions.defaultsFrom(options)

    @Test
    fun `defaults come from launch's defaults`() {
        expectThat(defaults).isEqualTo(ProjectOptions(defaultType, java, gradleKotlin, junit, jdk21))
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
    ) = Language(extension = "", description = "", name = "", value = value, label = "", defaults = defaults)
}

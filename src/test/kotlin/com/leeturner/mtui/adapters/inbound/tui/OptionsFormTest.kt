package com.leeturner.mtui.adapters.inbound.tui

import arrow.core.nonEmptyListOf
import com.leeturner.mtui.domain.core.model.ApplicationType
import com.leeturner.mtui.domain.core.model.BuildType
import com.leeturner.mtui.domain.core.model.JdkVersion
import com.leeturner.mtui.domain.core.model.Language
import com.leeturner.mtui.domain.core.model.LanguageDefaults
import com.leeturner.mtui.domain.core.model.SelectOptions
import com.leeturner.mtui.domain.core.model.TestFramework
import org.junit.jupiter.api.Test
import strikt.api.expectThat
import strikt.assertions.isEqualTo

class OptionsFormTest {
    private val types =
        nonEmptyListOf(
            type("DEFAULT", "Micronaut Application"),
            type("CLI", "Command Line Application"),
            type("FUNCTION", "Function Application for Serverless"),
            type("GRPC", "gRPC Application"),
            type("MESSAGING", "Messaging-Driven Application"),
        )
    private val java = language("JAVA", "Java", LanguageDefaults(test = "JUNIT", build = "GRADLE_KOTLIN"))
    private val groovy = language("GROOVY", "Groovy", LanguageDefaults(test = "SPOCK", build = "GRADLE_KOTLIN"))
    private val kotlin = language("KOTLIN", "Kotlin", LanguageDefaults(test = "JUNIT", build = "GRADLE_KOTLIN"))
    private val tests =
        nonEmptyListOf(
            TestFramework("", "", "JUNIT", "JUnit"),
            TestFramework("", "", "SPOCK", "Spock"),
            TestFramework("", "", "KOTEST", "Kotest"),
        )
    private val builds =
        nonEmptyListOf(
            BuildType("", "GRADLE", "Gradle"),
            BuildType("", "GRADLE_KOTLIN", "Gradle Kotlin"),
            BuildType("", "MAVEN", "Maven"),
        )
    private val jdks =
        nonEmptyListOf(
            JdkVersion("", "", "JDK_17", "17"),
            JdkVersion("", "", "JDK_21", "21"),
            JdkVersion("", "", "JDK_25", "25"),
        )
    private val options =
        SelectOptions(
            types = types,
            defaultType = types.first(),
            jdkVersions = jdks,
            defaultJdkVersion = jdks[1],
            languages = nonEmptyListOf(java, groovy, kotlin),
            defaultLanguage = java,
            testFrameworks = tests,
            defaultTestFramework = tests.first(),
            buildTypes = builds,
            defaultBuildType = builds[1],
        )
    private val form = OptionsForm(options)

    @Test
    fun `starts on launch's defaults`() {
        expectThat(form.choices(OptionRow.LANGUAGE))
            .isEqualTo(listOf(Choice("Java", true), Choice("Groovy", false), Choice("Kotlin", false)))
    }

    @Test
    fun `type uses short labels`() {
        expectThat(form.choices(OptionRow.TYPE).map { it.label })
            .isEqualTo(listOf("Default", "CLI", "Function", "gRPC", "Messaging"))
    }

    @Test
    fun `unknown type falls back to launch's label`() {
        val withNewType = options.copy(types = nonEmptyListOf(types.first(), type("BATCH", "Batch Application")))

        expectThat(OptionsForm(withNewType).choices(OptionRow.TYPE).map { it.label })
            .isEqualTo(listOf("Default", "Batch Application"))
    }

    @Test
    fun `next and previous move the choice and stop at the ends`() {
        form.next(OptionRow.JDK)
        expectThat(form.chosen.jdk.label).isEqualTo("25")

        form.next(OptionRow.JDK)
        expectThat(form.chosen.jdk.label).isEqualTo("25")

        repeat(3) { form.previous(OptionRow.JDK) }
        expectThat(form.chosen.jdk.label).isEqualTo("17")
    }

    @Test
    fun `changing language applies its defaults`() {
        form.next(OptionRow.LANGUAGE)

        expectThat(form.chosen.language).isEqualTo(groovy)
        expectThat(form.chosen.test.value).isEqualTo("SPOCK")
    }

    @Test
    fun `moving past the last language keeps the chosen test framework`() {
        repeat(2) { form.next(OptionRow.LANGUAGE) }
        form.next(OptionRow.TEST)

        form.next(OptionRow.LANGUAGE)

        expectThat(form.chosen.test.value).isEqualTo("SPOCK")
    }

    private fun type(
        value: String,
        label: String,
    ) = ApplicationType(title = "", name = "", description = "", value = value, label = label)

    private fun language(
        value: String,
        label: String,
        defaults: LanguageDefaults,
    ) = Language(extension = "", description = "", name = "", value = value, label = label, defaults = defaults)
}

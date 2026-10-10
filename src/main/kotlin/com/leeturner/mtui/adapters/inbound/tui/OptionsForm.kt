package com.leeturner.mtui.adapters.inbound.tui

import com.leeturner.mtui.domain.core.model.ProjectOptions
import com.leeturner.mtui.domain.core.model.SelectOptions
import com.leeturner.mtui.domain.core.model.withLanguage

enum class OptionRow(
    val label: String,
) {
    TYPE("Type"),
    LANGUAGE("Language"),
    BUILD("Build"),
    TEST("Test"),
    JDK("JDK"),
}

data class Choice(
    val label: String,
    val chosen: Boolean,
)

// The option rows on the name screen, kept free of TamboUI so it can be unit tested
class OptionsForm(
    private val options: SelectOptions,
) {
    var chosen: ProjectOptions = ProjectOptions.defaultsFrom(options)
        private set

    fun choices(row: OptionRow): List<Choice> =
        when (row) {
            OptionRow.TYPE -> options.types.map { Choice(SHORT_TYPE_LABELS[it.value] ?: it.label, it == chosen.type) }
            OptionRow.LANGUAGE -> options.languages.map { Choice(it.label, it == chosen.language) }
            OptionRow.BUILD -> options.buildTypes.map { Choice(it.label, it == chosen.build) }
            OptionRow.TEST -> options.testFrameworks.map { Choice(it.label, it == chosen.test) }
            OptionRow.JDK -> options.jdkVersions.map { Choice(it.label, it == chosen.jdk) }
        }

    fun next(row: OptionRow) = step(row, 1)

    fun previous(row: OptionRow) = step(row, -1)

    private fun step(
        row: OptionRow,
        by: Int,
    ) {
        chosen =
            when (row) {
                OptionRow.TYPE -> {
                    chosen.copy(type = options.types.stepFrom(chosen.type, by))
                }

                // Only a real change applies the language's defaults, so pressing past the end keeps Test and Build
                OptionRow.LANGUAGE -> {
                    val language = options.languages.stepFrom(chosen.language, by)
                    if (language == chosen.language) chosen else chosen.withLanguage(language, options)
                }

                OptionRow.BUILD -> {
                    chosen.copy(build = options.buildTypes.stepFrom(chosen.build, by))
                }

                OptionRow.TEST -> {
                    chosen.copy(test = options.testFrameworks.stepFrom(chosen.test, by))
                }

                OptionRow.JDK -> {
                    chosen.copy(jdk = options.jdkVersions.stepFrom(chosen.jdk, by))
                }
            }
    }

    private companion object {
        // Launch's type labels, e.g. "Function Application for Serverless", are too long for one row
        val SHORT_TYPE_LABELS =
            mapOf(
                "DEFAULT" to "Default",
                "CLI" to "CLI",
                "FUNCTION" to "Function",
                "GRPC" to "gRPC",
                "MESSAGING" to "Messaging",
            )
    }
}

// Stops at the ends rather than wrapping
private fun <T> List<T>.stepFrom(
    current: T,
    by: Int,
): T = this[(indexOf(current) + by).coerceIn(indices)]

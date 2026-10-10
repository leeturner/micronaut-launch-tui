package com.leeturner.mtui.domain.core.model

// The options the user has chosen for the project, starting from Launch's defaults
data class ProjectOptions(
    val type: ApplicationType,
    val language: Language,
    val build: BuildType,
    val test: TestFramework,
    val jdk: JdkVersion,
) {
    companion object {
        fun defaultsFrom(options: SelectOptions): ProjectOptions =
            ProjectOptions(
                type = options.defaultType,
                language = options.defaultLanguage,
                build = options.defaultBuildType,
                test = options.defaultTestFramework,
                jdk = options.defaultJdkVersion,
            )
    }
}

// Each language has its own test and build defaults; one Launch doesn't offer leaves the current choice alone
fun ProjectOptions.withLanguage(
    language: Language,
    options: SelectOptions,
): ProjectOptions {
    val defaults = language.defaults ?: return copy(language = language)
    return copy(
        language = language,
        test = options.testFrameworks.firstOrNull { it.value == defaults.test } ?: test,
        build = options.buildTypes.firstOrNull { it.value == defaults.build } ?: build,
    )
}

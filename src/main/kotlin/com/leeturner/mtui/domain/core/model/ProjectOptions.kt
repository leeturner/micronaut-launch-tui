package com.leeturner.mtui.domain.core.model

// The options the user has chosen for the project, starting from Launch's defaults
data class ProjectOptions(
    val version: MicronautVersion,
    val type: ApplicationType,
    val language: Language,
    val build: BuildType,
    val test: TestFramework,
    val jdk: JdkVersion,
) {
    companion object {
        fun defaultsFrom(
            options: SelectOptions,
            version: MicronautVersion,
        ): ProjectOptions =
            ProjectOptions(
                version = version,
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

// Another server's options are separate objects, so each choice is matched by value; language defaults aren't applied
fun ProjectOptions.onServer(
    version: MicronautVersion,
    options: SelectOptions,
): ProjectOptions =
    ProjectOptions(
        version = version,
        type = options.types.firstOrNull { it.value == type.value } ?: options.defaultType,
        language = options.languages.firstOrNull { it.value == language.value } ?: options.defaultLanguage,
        build = options.buildTypes.firstOrNull { it.value == build.value } ?: options.defaultBuildType,
        test = options.testFrameworks.firstOrNull { it.value == test.value } ?: options.defaultTestFramework,
        jdk = options.jdkVersions.firstOrNull { it.value == jdk.value } ?: options.defaultJdkVersion,
    )

// Features depend only on server, type and language, so changing build, test or JDK keeps the fetched list
fun ProjectOptions.hasSameFeaturesAs(other: ProjectOptions?): Boolean =
    other != null && version.server == other.version.server && type == other.type && language == other.language

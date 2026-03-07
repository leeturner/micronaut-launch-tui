package com.leeturner.mtui.domain.core.model

import arrow.core.NonEmptyList

data class SelectOptions(
    val types: NonEmptyList<ApplicationType>,
    val defaultType: ApplicationType,
    val jdkVersions: NonEmptyList<JdkVersion>,
    val defaultJdkVersion: JdkVersion,
    val languages: NonEmptyList<Language>,
    val defaultLanguage: Language,
    val testFrameworks: NonEmptyList<TestFramework>,
    val defaultTestFramework: TestFramework,
    val buildTypes: NonEmptyList<BuildType>,
    val defaultBuildType: BuildType,
)

data class ApplicationType(
    val title: String,
    val name: String,
    val description: String,
    val value: String,
    val label: String,
)

data class JdkVersion(
    val description: String,
    val name: String,
    val value: String,
    val label: String,
)

data class Language(
    val extension: String,
    val description: String,
    val name: String,
    val value: String,
    val label: String,
    val defaults: Map<String, String> = mapOf(),
)

data class TestFramework(
    val description: String,
    val name: String,
    val value: String,
    val label: String,
)

data class BuildType(
    val description: String,
    val value: String,
    val label: String,
)

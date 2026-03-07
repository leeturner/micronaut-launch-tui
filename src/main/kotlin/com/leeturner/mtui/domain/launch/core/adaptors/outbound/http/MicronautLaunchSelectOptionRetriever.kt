package com.leeturner.mtui.domain.launch.core.adaptors.outbound.http

import arrow.core.Either
import arrow.core.raise.either
import arrow.core.toNonEmptyListOrNull
import com.leeturner.mtui.domain.launch.core.model.ApplicationType
import com.leeturner.mtui.domain.launch.core.model.BuildType
import com.leeturner.mtui.domain.launch.core.model.JdkVersion
import com.leeturner.mtui.domain.launch.core.model.Language
import com.leeturner.mtui.domain.launch.core.model.SelectOptions
import com.leeturner.mtui.domain.launch.core.model.SelectOptionsError
import com.leeturner.mtui.domain.launch.core.model.TestFramework
import com.leeturner.mtui.domain.launch.core.model.UnexpectedSelectOptionRetrievalError
import com.leeturner.mtui.domain.launch.core.ports.SelectOptionRetriever
import com.leeturner.mtui.domain.launch.infrastructure.api.MicronautLaunchDefaultApi
import com.leeturner.mtui.domain.launch.infrastructure.model.MicronautLaunchApplicationTypeInfo
import com.leeturner.mtui.domain.launch.infrastructure.model.MicronautLaunchBuildToolInfo
import com.leeturner.mtui.domain.launch.infrastructure.model.MicronautLaunchJdkVersionInfo
import com.leeturner.mtui.domain.launch.infrastructure.model.MicronautLaunchLanguageInfo
import com.leeturner.mtui.domain.launch.infrastructure.model.MicronautLaunchSelectOptions
import com.leeturner.mtui.domain.launch.infrastructure.model.MicronautLaunchTestFrameworkInfo
import io.micronaut.http.client.exceptions.HttpClientResponseException
import jakarta.inject.Inject
import jakarta.inject.Singleton

@Singleton
class MicronautLaunchSelectOptionRetriever(
    @Inject private val micronautLaunchDefaultApi: MicronautLaunchDefaultApi,
) : SelectOptionRetriever {
    override fun getSelectOptions(): Either<SelectOptionsError, SelectOptions> =
        either {
            try {
                val micronautLaunchSelectOptions = micronautLaunchDefaultApi.selectOptions()
                micronautLaunchSelectOptions.toSelectOptions()
            } catch (e: HttpClientResponseException) {
                raise(
                    UnexpectedSelectOptionRetrievalError(
                        status = e.status.code,
                        message = e.message ?: "Unknown error",
                    ),
                )
            }
        }
}

private fun MicronautLaunchSelectOptions.toSelectOptions(): SelectOptions {
    val types =
        type?.options?.map { it.toApplicationType() }?.toNonEmptyListOrNull()
            ?: error("No application types found in Micronaut Launch select options")
    val defaultType = type?.defaultOption?.toApplicationType() ?: types.head

    val jdkVersions =
        jdkVersion?.options?.map { it.toJdkVersion() }?.toNonEmptyListOrNull()
            ?: error("No JDK versions found in Micronaut Launch select options")
    val defaultJdkVersion = jdkVersion?.defaultOption?.toJdkVersion() ?: jdkVersions.head

    val languages =
        lang?.options?.map { it.toLanguage() }?.toNonEmptyListOrNull()
            ?: error("No languages found in Micronaut Launch select options")
    val defaultLanguage = lang?.defaultOption?.toLanguage() ?: languages.head

    val testFrameworks =
        test?.options?.map { it.toTestFramework() }?.toNonEmptyListOrNull()
            ?: error("No test frameworks found in Micronaut Launch select options")
    val defaultTestFramework = test?.defaultOption?.toTestFramework() ?: testFrameworks.head

    val buildTypes =
        build?.options?.map { it.toBuildType() }?.toNonEmptyListOrNull()
            ?: error("No build types found in Micronaut Launch select options")
    val defaultBuildType = build?.defaultOption?.toBuildType() ?: buildTypes.head

    return SelectOptions(
        types = types,
        defaultType = defaultType,
        jdkVersions = jdkVersions,
        defaultJdkVersion = defaultJdkVersion,
        languages = languages,
        defaultLanguage = defaultLanguage,
        testFrameworks = testFrameworks,
        defaultTestFramework = defaultTestFramework,
        buildTypes = buildTypes,
        defaultBuildType = defaultBuildType,
    )
}

private fun MicronautLaunchApplicationTypeInfo.toApplicationType(): ApplicationType =
    ApplicationType(
        title = title ?: "",
        name = name,
        description = description ?: "",
        value = value?.value ?: "",
        label = label ?: "",
    )

private fun MicronautLaunchJdkVersionInfo.toJdkVersion(): JdkVersion =
    JdkVersion(
        description = description ?: "",
        name = name,
        value = value,
        label = label ?: "",
    )

private fun MicronautLaunchLanguageInfo.toLanguage(): Language =
    Language(
        extension = extension ?: "",
        description = description ?: "",
        name = name,
        value = value?.value ?: "",
        label = label ?: "",
        defaults =
            defaults?.let {
                mapOf(
                    "test" to it.test.value,
                    "build" to it.build.value,
                )
            } ?: emptyMap(),
    )

private fun MicronautLaunchTestFrameworkInfo.toTestFramework(): TestFramework =
    TestFramework(
        description = description ?: "",
        name = name,
        value = value.value,
        label = label ?: "",
    )

private fun MicronautLaunchBuildToolInfo.toBuildType(): BuildType =
    BuildType(
        description = description,
        value = value.value,
        label = label,
    )

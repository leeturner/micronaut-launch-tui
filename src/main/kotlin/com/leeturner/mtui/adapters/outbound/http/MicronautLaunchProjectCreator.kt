package com.leeturner.mtui.adapters.outbound.http

import arrow.core.Either
import arrow.core.raise.Raise
import arrow.core.raise.either
import com.fasterxml.jackson.annotation.JsonProperty
import com.leeturner.mtui.adapters.outbound.http.client.api.MicronautLaunchDefaultApi
import com.leeturner.mtui.adapters.outbound.http.client.model.MicronautLaunchApplicationType
import com.leeturner.mtui.adapters.outbound.http.client.model.MicronautLaunchBuildTool
import com.leeturner.mtui.adapters.outbound.http.client.model.MicronautLaunchLanguage
import com.leeturner.mtui.adapters.outbound.http.client.model.MicronautLaunchTestFramework1
import com.leeturner.mtui.domain.core.model.GenerateProjectError
import com.leeturner.mtui.domain.core.model.ProjectName
import com.leeturner.mtui.domain.core.model.ProjectOptions
import com.leeturner.mtui.domain.core.model.ProjectRejected
import com.leeturner.mtui.domain.core.model.UnexpectedProjectCreationError
import com.leeturner.mtui.domain.core.ports.ProjectCreator
import io.micronaut.http.HttpStatus
import io.micronaut.http.client.exceptions.HttpClientException
import io.micronaut.http.client.exceptions.HttpClientResponseException
import io.micronaut.serde.annotation.Serdeable
import jakarta.inject.Singleton

@Singleton
class MicronautLaunchProjectCreator(
    private val micronautLaunchDefaultApi: MicronautLaunchDefaultApi,
) : ProjectCreator {
    override fun createProject(
        options: ProjectOptions,
        name: ProjectName,
        features: List<String>,
    ): Either<GenerateProjectError, ByteArray> =
        either {
            val type = lookup(MicronautLaunchApplicationType.VALUE_MAPPING, "application type", options.type.value)
            val lang = lookup(MicronautLaunchLanguage.VALUE_MAPPING, "language", options.language.value)
            val build = lookup(MicronautLaunchBuildTool.VALUE_MAPPING, "build type", options.build.value)
            // createApp's test parameter uses Launch's second test framework enum, which also has KOTLINTEST
            val test = lookup(MicronautLaunchTestFramework1.VALUE_MAPPING, "test framework", options.test.value)
            try {
                micronautLaunchDefaultApi
                    .createApp(
                        type = type,
                        name = name.value,
                        features = features.ifEmpty { null },
                        build = build,
                        test = test,
                        lang = lang,
                        javaVersion = options.jdk.value,
                    ).toByteArray()
            } catch (e: HttpClientResponseException) {
                if (e.status == HttpStatus.BAD_REQUEST) {
                    raise(ProjectRejected(e.launchMessage() ?: e.status.reason))
                }
                raise(UnexpectedProjectCreationError(status = e.status.code, message = e.message ?: "Unknown error"))
            } catch (e: HttpClientException) {
                raise(UnexpectedProjectCreationError(status = null, message = e.message ?: "Unknown error"))
            }
        }
}

private fun <T> Raise<GenerateProjectError>.lookup(
    mapping: Map<String, T>,
    what: String,
    value: String,
): T = mapping[value] ?: raise(UnexpectedProjectCreationError(null, "Unsupported $what: $value"))

// Launch puts the readable message in _embedded.errors[0].message; the top-level message is just "Bad Request"
private fun HttpClientResponseException.launchMessage(): String? =
    runCatching { response.getBody(LaunchErrorBody::class.java).orElse(null) }
        .getOrNull()
        ?.embedded
        ?.errors
        ?.firstOrNull()
        ?.message

@Serdeable
private data class LaunchErrorBody(
    @JsonProperty("_embedded") val embedded: Embedded?,
) {
    @Serdeable
    data class Embedded(
        val errors: List<Error>?,
    )

    @Serdeable
    data class Error(
        val message: String?,
    )
}

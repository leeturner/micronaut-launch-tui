package com.leeturner.mtui.adapters.outbound.http

import arrow.core.Either
import arrow.core.raise.either
import com.fasterxml.jackson.annotation.JsonProperty
import com.leeturner.mtui.adapters.outbound.http.client.api.MicronautLaunchDefaultApi
import com.leeturner.mtui.adapters.outbound.http.client.model.MicronautLaunchApplicationType
import com.leeturner.mtui.domain.core.model.ApplicationType
import com.leeturner.mtui.domain.core.model.GenerateProjectError
import com.leeturner.mtui.domain.core.model.ProjectName
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
        type: ApplicationType,
        name: ProjectName,
    ): Either<GenerateProjectError, ByteArray> =
        either {
            val launchType =
                MicronautLaunchApplicationType.VALUE_MAPPING[type.value]
                    ?: raise(UnexpectedProjectCreationError(null, "Unsupported application type: ${type.value}"))
            try {
                micronautLaunchDefaultApi.createApp(launchType, name.value).toByteArray()
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

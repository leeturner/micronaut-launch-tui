package com.leeturner.mtui.adapters.outbound.http

import arrow.core.Either
import arrow.core.flatMap
import arrow.core.left
import arrow.core.raise.Raise
import arrow.core.raise.either
import arrow.core.right
import com.fasterxml.jackson.annotation.JsonProperty
import com.leeturner.mtui.adapters.outbound.http.client.api.MicronautLaunchDefaultApi
import com.leeturner.mtui.adapters.outbound.http.client.model.MicronautLaunchApplicationType
import com.leeturner.mtui.adapters.outbound.http.client.model.MicronautLaunchBuildTool
import com.leeturner.mtui.adapters.outbound.http.client.model.MicronautLaunchLanguage
import com.leeturner.mtui.adapters.outbound.http.client.model.MicronautLaunchTestFramework1
import com.leeturner.mtui.domain.core.model.GenerateProjectError
import com.leeturner.mtui.domain.core.model.ProjectFile
import com.leeturner.mtui.domain.core.model.ProjectName
import com.leeturner.mtui.domain.core.model.ProjectOptions
import com.leeturner.mtui.domain.core.model.ProjectPreview
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
        callLaunch(options) { p ->
            micronautLaunchDefaultApi
                .createApp(
                    type = p.type,
                    name = name.value,
                    features = features.ifEmpty { null },
                    build = p.build,
                    test = p.test,
                    lang = p.lang,
                    javaVersion = options.jdk.value,
                )
        }.map { it.toByteArray() }

    override fun previewProject(
        options: ProjectOptions,
        name: ProjectName,
        features: List<String>,
    ): Either<GenerateProjectError, ProjectPreview> =
        callLaunch(options) { p ->
            micronautLaunchDefaultApi.previewApp(
                type = p.type,
                name = name.value,
                features = features.ifEmpty { null },
                build = p.build,
                test = p.test,
                lang = p.lang,
                javaVersion = options.jdk.value,
            )
        }.flatMap { preview ->
            // Launch sends null for binary files, which can't be shown
            val files =
                preview.contents.orEmpty().mapNotNull { (path, content) -> content?.let { ProjectFile(path, it) } }
            if (files.isEmpty()) {
                UnexpectedProjectCreationError("Launch returned no files to preview").left()
            } else {
                ProjectPreview(files).right()
            }
        }

    // Maps the chosen options to the client's enums and calls the chosen server, turning failures into errors
    private fun <T : Any> callLaunch(
        options: ProjectOptions,
        call: (LaunchParams) -> T?,
    ): Either<GenerateProjectError, T> =
        either {
            val params =
                LaunchParams(
                    type = lookup(MicronautLaunchApplicationType.VALUE_MAPPING, "application type", options.type.value),
                    lang = lookup(MicronautLaunchLanguage.VALUE_MAPPING, "language", options.language.value),
                    build = lookup(MicronautLaunchBuildTool.VALUE_MAPPING, "build type", options.build.value),
                    // The test parameter uses Launch's second test framework enum, which also has KOTLINTEST
                    test = lookup(MicronautLaunchTestFramework1.VALUE_MAPPING, "test framework", options.test.value),
                )
            try {
                onServer(options.version.server) { call(params) }
            } catch (e: HttpClientResponseException) {
                if (e.status == HttpStatus.BAD_REQUEST) {
                    raise(ProjectRejected(e.launchMessage() ?: e.status.reason))
                }
                raise(UnexpectedProjectCreationError(e.message ?: "Unknown error"))
            } catch (e: HttpClientException) {
                raise(UnexpectedProjectCreationError(e.message ?: "Unknown error"))
            }
        }
}

private class LaunchParams(
    val type: MicronautLaunchApplicationType,
    val lang: MicronautLaunchLanguage,
    val build: MicronautLaunchBuildTool,
    val test: MicronautLaunchTestFramework1,
)

private fun <T> Raise<GenerateProjectError>.lookup(
    mapping: Map<String, T>,
    what: String,
    value: String,
): T = mapping[value] ?: raise(UnexpectedProjectCreationError("Unsupported $what: $value"))

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

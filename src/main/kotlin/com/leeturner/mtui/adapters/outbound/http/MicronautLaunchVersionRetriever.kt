package com.leeturner.mtui.adapters.outbound.http

import arrow.core.Either
import arrow.core.raise.either
import com.leeturner.mtui.adapters.outbound.http.client.api.MicronautLaunchDefaultApi
import com.leeturner.mtui.domain.core.model.LaunchServer
import com.leeturner.mtui.domain.core.model.VersionError
import com.leeturner.mtui.domain.core.ports.VersionRetriever
import io.micronaut.http.client.exceptions.HttpClientException
import io.micronaut.http.client.exceptions.HttpClientResponseException
import jakarta.inject.Singleton

@Singleton
class MicronautLaunchVersionRetriever(
    private val micronautLaunchDefaultApi: MicronautLaunchDefaultApi,
) : VersionRetriever {
    override fun getMicronautVersion(server: LaunchServer): Either<VersionError, String> =
        either {
            val versions =
                try {
                    onServer(server) { micronautLaunchDefaultApi.getInfo() }.versions
                } catch (e: HttpClientException) {
                    raise(
                        VersionError(
                            status = (e as? HttpClientResponseException)?.status?.code,
                            message = e.message ?: "Unknown error",
                        ),
                    )
                }
            versions?.get("micronaut.version") ?: raise(VersionError(null, "No micronaut.version in /versions"))
        }
}

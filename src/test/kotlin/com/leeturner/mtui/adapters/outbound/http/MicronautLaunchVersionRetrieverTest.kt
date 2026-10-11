package com.leeturner.mtui.adapters.outbound.http

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder
import com.github.tomakehurst.wiremock.client.WireMock
import com.leeturner.mtui.domain.core.model.LaunchServer
import com.leeturner.mtui.domain.core.model.VersionError
import com.leeturner.wiremock.micronaut.ConfigureWireMock
import com.leeturner.wiremock.micronaut.EnableWireMock
import com.leeturner.wiremock.micronaut.InjectWireMock
import io.micronaut.test.extensions.junit5.annotation.MicronautTest
import jakarta.inject.Inject
import org.junit.jupiter.api.Test
import strikt.api.expectThat
import strikt.arrow.isLeft
import strikt.arrow.isRight
import strikt.assertions.isEmpty
import strikt.assertions.isEqualTo

@MicronautTest
@EnableWireMock(
    ConfigureWireMock(
        name = "micronaut-launch",
        baseUrlProperties = ["micronaut.http.services.micronaut-launch.url"],
    ),
    ConfigureWireMock(name = "snapshot", baseUrlProperties = ["mtui.launch.snapshot-url"]),
)
class MicronautLaunchVersionRetrieverTest {
    @InjectWireMock("micronaut-launch")
    private lateinit var wireMock: WireMockServer

    @InjectWireMock("snapshot")
    private lateinit var snapshot: WireMockServer

    @Inject
    lateinit var retriever: MicronautLaunchVersionRetriever

    @Test
    fun `200 returns the micronaut version`() {
        stubVersions(okJson("/payloads/get-versions.json"))

        expectThat(retriever.getMicronautVersion(LaunchServer.RELEASE)).isRight().get { value }.isEqualTo("5.2.2")
    }

    @Test
    fun `missing micronaut version is an error`() {
        stubVersions(okJson("/payloads/get-versions-missing-micronaut.json"))

        expectThat(retriever.getMicronautVersion(LaunchServer.RELEASE))
            .isLeft()
            .get { value }
            .isEqualTo(VersionError(null, "No micronaut.version in /versions"))
    }

    @Test
    fun `500 is an error with its status`() {
        stubVersions(WireMock.aResponse().withStatus(500))

        expectThat(retriever.getMicronautVersion(LaunchServer.RELEASE)).isLeft().get { value.status }.isEqualTo(500)
    }

    @Test
    fun `snapshot server is called when it is chosen`() {
        stubVersions(okJson("/payloads/get-versions.json"), on = snapshot)

        expectThat(retriever.getMicronautVersion(LaunchServer.SNAPSHOT)).isRight()
        expectThat(wireMock.allServeEvents).isEmpty()
    }

    private fun okJson(payload: String) = WireMock.okJson(javaClass.getResource(payload)!!.readText())

    private fun stubVersions(
        response: ResponseDefinitionBuilder,
        on: WireMockServer = wireMock,
    ) {
        on.stubFor(WireMock.get(WireMock.urlPathEqualTo("/versions")).willReturn(response))
    }
}

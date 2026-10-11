package com.leeturner.mtui.adapters.outbound.http

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder
import com.github.tomakehurst.wiremock.client.WireMock
import com.leeturner.mtui.domain.core.model.CatalogError
import com.leeturner.mtui.domain.core.model.LaunchServer
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
class MicronautLaunchCatalogVersionTest {
    @InjectWireMock("micronaut-launch")
    private lateinit var wireMock: WireMockServer

    @InjectWireMock("snapshot")
    private lateinit var snapshot: WireMockServer

    @Inject
    lateinit var catalog: MicronautLaunchCatalog

    @Test
    fun `200 returns the micronaut version`() {
        stubVersions(okJson("/payloads/get-versions.json"))

        expectThat(catalog.getMicronautVersion(LaunchServer.RELEASE)).isRight().get { value }.isEqualTo("5.2.2")
    }

    @Test
    fun `missing micronaut version is an error`() {
        stubVersions(okJson("/payloads/get-versions-missing-micronaut.json"))

        expectThat(catalog.getMicronautVersion(LaunchServer.RELEASE))
            .isLeft()
            .get { value }
            .isEqualTo(CatalogError("No micronaut.version in /versions"))
    }

    @Test
    fun `500 is an error`() {
        stubVersions(WireMock.aResponse().withStatus(500))

        expectThat(catalog.getMicronautVersion(LaunchServer.RELEASE)).isLeft()
    }

    @Test
    fun `404 is an error`() {
        stubVersions(WireMock.aResponse().withStatus(404))

        expectThat(catalog.getMicronautVersion(LaunchServer.RELEASE)).isLeft()
    }

    @Test
    fun `snapshot server is called when it is chosen`() {
        stubVersions(okJson("/payloads/get-versions.json"), on = snapshot)

        expectThat(catalog.getMicronautVersion(LaunchServer.SNAPSHOT)).isRight()
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

package com.leeturner.mtui.adaptors.outbound.http

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock
import com.leeturner.mtui.adaptors.outbound.http.MicronautLaunchSelectOptionRetriever
import io.github.nahuel92.wiremock.micronaut.ConfigureWireMock
import io.github.nahuel92.wiremock.micronaut.InjectWireMock
import io.github.nahuel92.wiremock.micronaut.MicronautWireMockTest
import jakarta.inject.Inject
import org.junit.jupiter.api.Test
import strikt.api.expectThat
import strikt.arrow.isRight
import strikt.assertions.hasSize
import strikt.assertions.isEqualTo
import strikt.assertions.map
import java.nio.file.Files
import java.nio.file.Paths

@MicronautWireMockTest(
  ConfigureWireMock(
    name = "micronaut-launch",
    properties = ["micronaut.http.services.micronaut-launch.url"],
  ),
)
class MicronautLaunchSelectOptionRetrieverTest {
    @InjectWireMock("micronaut-launch")
    private lateinit var wireMock: WireMockServer

    @Inject
    lateinit var selectOptionsRetriever: MicronautLaunchSelectOptionRetriever

    @Test
    fun `getSelectOptions returns correct data from micronaut launch api`() {
        val jsonPayload = Files.readString(Paths.get("src/test/resources/payloads/get-select-options.json"))

        wireMock.stubFor(
            WireMock.get(WireMock.urlEqualTo("/select-options"))
                .willReturn(
                    WireMock.aResponse()
                        .withHeader("Content-Type", "application/json")
                        .withBody(jsonPayload),
                ),
        )

        val result = selectOptionsRetriever.getSelectOptions()

        expectThat(result).isRight().and {
            get { value.defaultType.value }.isEqualTo("DEFAULT")
            get { value.types }.hasSize(5).map { it.value }.isEqualTo(
                listOf("DEFAULT", "CLI", "FUNCTION", "GRPC", "MESSAGING"),
            )

            get { value.defaultJdkVersion.value }.isEqualTo("JDK_21")
            get { value.jdkVersions }.hasSize(3).map { it.value }.isEqualTo(
                listOf("JDK_17", "JDK_21", "JDK_25"),
            )

            get { value.defaultLanguage.value }.isEqualTo("JAVA")
            get { value.languages }.hasSize(3).map { it.value }.isEqualTo(
                listOf("JAVA", "GROOVY", "KOTLIN"),
            )

            get { value.defaultTestFramework.value }.isEqualTo("JUNIT")
            get { value.testFrameworks }.hasSize(3).map { it.value }.isEqualTo(
                listOf("JUNIT", "SPOCK", "KOTEST"),
            )

            get { value.defaultBuildType.value }.isEqualTo("GRADLE_KOTLIN")
            get { value.buildTypes }.hasSize(3).map { it.value }.isEqualTo(
                listOf("GRADLE", "GRADLE_KOTLIN", "MAVEN"),
            )
        }
    }
}

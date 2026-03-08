package com.leeturner.mtui.adapters.outbound.http

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock
import com.leeturner.mtui.domain.core.model.EmptySelectOptionsError
import com.leeturner.mtui.domain.core.model.UnexpectedSelectOptionRetrievalError
import io.github.nahuel92.wiremock.micronaut.ConfigureWireMock
import io.github.nahuel92.wiremock.micronaut.InjectWireMock
import io.github.nahuel92.wiremock.micronaut.MicronautWireMockTest
import jakarta.inject.Inject
import org.junit.jupiter.api.Test
import strikt.api.expectThat
import strikt.arrow.isLeft
import strikt.arrow.isRight
import strikt.assertions.hasSize
import strikt.assertions.isA
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
            WireMock
                .get(WireMock.urlEqualTo("/select-options"))
                .willReturn(
                    WireMock
                        .aResponse()
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

    @Test
    fun `getSelectOptions returns a left when the api returns a non-200 response`() {
        wireMock.stubFor(
            WireMock
                .get(WireMock.urlEqualTo("/select-options"))
                .willReturn(
                    WireMock
                        .aResponse()
                        .withStatus(500),
                ),
        )

        val result = selectOptionsRetriever.getSelectOptions()

        expectThat(result).isLeft().and {
            get { value }.isA<UnexpectedSelectOptionRetrievalError>().and {
                get { status }.isEqualTo(500)
                get { message }.isEqualTo("Client 'micronaut-launch': Server Error")
            }
        }
    }

    @Test
    fun `getSelectOptions returns a left when the api returns an empty list of application types`() {
        val jsonPayload =
            Files.readString(Paths.get("src/test/resources/payloads/get-select-options-empty-types.json"))

        wireMock.stubFor(
            WireMock
                .get(WireMock.urlEqualTo("/select-options"))
                .willReturn(
                    WireMock
                        .aResponse()
                        .withHeader("Content-Type", "application/json")
                        .withBody(jsonPayload),
                ),
        )

        val result = selectOptionsRetriever.getSelectOptions()

        expectThat(result).isLeft().and {
            get { value }.isA<EmptySelectOptionsError>().and {
                get { message }.isEqualTo("No application types found in Micronaut Launch select options")
            }
        }
    }

    @Test
    fun `getSelectOptions returns a left when an application type is missing its value`() {
        val jsonPayload =
            Files.readString(Paths.get("src/test/resources/payloads/get-select-options-missing-type-value.json"))

        wireMock.stubFor(
            WireMock
                .get(WireMock.urlEqualTo("/select-options"))
                .willReturn(
                    WireMock
                        .aResponse()
                        .withHeader("Content-Type", "application/json")
                        .withBody(jsonPayload),
                ),
        )

        val result = selectOptionsRetriever.getSelectOptions()

        expectThat(result).isLeft().and {
            get { value }.isA<EmptySelectOptionsError>().and {
                get { message }.isEqualTo("Missing value for application type 'default'")
            }
        }
    }

    @Test
    fun `getSelectOptions returns a left when a language is missing its value`() {
        val jsonPayload =
            Files.readString(Paths.get("src/test/resources/payloads/get-select-options-missing-language-value.json"))

        wireMock.stubFor(
            WireMock
                .get(WireMock.urlEqualTo("/select-options"))
                .willReturn(
                    WireMock
                        .aResponse()
                        .withHeader("Content-Type", "application/json")
                        .withBody(jsonPayload),
                ),
        )

        val result = selectOptionsRetriever.getSelectOptions()

        expectThat(result).isLeft().and {
            get { value }.isA<EmptySelectOptionsError>().and {
                get { message }.isEqualTo("Missing value for language 'java'")
            }
        }
    }
}

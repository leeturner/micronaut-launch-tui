package com.leeturner.mtui.adapters.outbound.http

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock
import com.github.tomakehurst.wiremock.http.Fault
import com.leeturner.mtui.domain.core.model.CatalogError
import com.leeturner.mtui.domain.core.model.LanguageDefaults
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
import strikt.assertions.hasSize
import strikt.assertions.isA
import strikt.assertions.isEmpty
import strikt.assertions.isEqualTo
import strikt.assertions.map

@MicronautTest
@EnableWireMock(
    ConfigureWireMock(
        name = "micronaut-launch",
        baseUrlProperties = ["micronaut.http.services.micronaut-launch.url"],
    ),
    ConfigureWireMock(name = "snapshot", baseUrlProperties = ["mtui.launch.snapshot-url"]),
)
class MicronautLaunchCatalogOptionsTest {
    @InjectWireMock("micronaut-launch")
    private lateinit var wireMock: WireMockServer

    @InjectWireMock("snapshot")
    private lateinit var snapshot: WireMockServer

    @Inject
    lateinit var catalog: MicronautLaunchCatalog

    @Test
    fun `getSelectOptions returns correct data from micronaut launch api`() {
        stubSelectOptions("get-select-options.json")

        val result = catalog.getSelectOptions(LaunchServer.RELEASE)

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
            get { value.defaultLanguage.defaults }.isEqualTo(LanguageDefaults(test = "JUNIT", build = "GRADLE_KOTLIN"))
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
    fun `getSelectOptions falls back to the first option when a default is missing or not in the options`() {
        stubSelectOptions("get-select-options-invalid-defaults.json")

        val result = catalog.getSelectOptions(LaunchServer.RELEASE)

        expectThat(result).isRight().and {
            get { value.defaultType.value }.isEqualTo("DEFAULT")
            get { value.defaultJdkVersion.value }.isEqualTo("JDK_17")
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

        val result = catalog.getSelectOptions(LaunchServer.RELEASE)

        expectThat(result).isLeft().and {
            get { value }.isA<CatalogError>().and {
                get { message }.isEqualTo("Client 'micronaut-launch': Server Error")
            }
        }
    }

    @Test
    fun `getSelectOptions returns a left when the connection fails`() {
        wireMock.stubFor(
            WireMock
                .get(WireMock.urlEqualTo("/select-options"))
                .willReturn(WireMock.aResponse().withFault(Fault.CONNECTION_RESET_BY_PEER)),
        )

        val result = catalog.getSelectOptions(LaunchServer.RELEASE)

        expectThat(result).isLeft()
    }

    @Test
    fun `getSelectOptions returns a left when the api returns an empty list of application types`() {
        stubSelectOptions("get-select-options-empty-types.json")

        val result = catalog.getSelectOptions(LaunchServer.RELEASE)

        expectThat(result).isLeft().and {
            get { value }.isA<CatalogError>().and {
                get { message }.isEqualTo("No application types found in Micronaut Launch select options")
            }
        }
    }

    @Test
    fun `getSelectOptions returns a left when an application type is missing its value`() {
        stubSelectOptions("get-select-options-missing-type-value.json")

        val result = catalog.getSelectOptions(LaunchServer.RELEASE)

        expectThat(result).isLeft().and {
            get { value }.isA<CatalogError>().and {
                get { message }.isEqualTo("Missing value for application type 'default'")
            }
        }
    }

    @Test
    fun `getSelectOptions returns a left when a language is missing its value`() {
        stubSelectOptions("get-select-options-missing-language-value.json")

        val result = catalog.getSelectOptions(LaunchServer.RELEASE)

        expectThat(result).isLeft().and {
            get { value }.isA<CatalogError>().and {
                get { message }.isEqualTo("Missing value for language 'java'")
            }
        }
    }

    @Test
    fun `snapshot server is called when it is chosen`() {
        stubSelectOptions("get-select-options.json", on = snapshot)

        expectThat(catalog.getSelectOptions(LaunchServer.SNAPSHOT)).isRight()
        expectThat(wireMock.allServeEvents).isEmpty()
    }

    @Test
    fun `release server is called when it is chosen`() {
        stubSelectOptions("get-select-options.json")

        expectThat(catalog.getSelectOptions(LaunchServer.RELEASE)).isRight()
        expectThat(snapshot.allServeEvents).isEmpty()
    }

    private fun stubSelectOptions(
        payloadFile: String,
        on: WireMockServer = wireMock,
    ) {
        val jsonPayload = javaClass.getResource("/payloads/$payloadFile")!!.readText()

        on.stubFor(
            WireMock
                .get(WireMock.urlEqualTo("/select-options"))
                .willReturn(
                    WireMock
                        .aResponse()
                        .withHeader("Content-Type", "application/json")
                        .withBody(jsonPayload),
                ),
        )
    }
}

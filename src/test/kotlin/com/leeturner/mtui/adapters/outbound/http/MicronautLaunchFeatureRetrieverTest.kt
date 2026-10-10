package com.leeturner.mtui.adapters.outbound.http

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder
import com.github.tomakehurst.wiremock.client.WireMock
import com.github.tomakehurst.wiremock.http.Fault
import com.leeturner.mtui.domain.core.model.ApplicationType
import com.leeturner.mtui.domain.core.model.Feature
import com.leeturner.mtui.domain.core.model.FeaturesError
import com.leeturner.mtui.domain.core.model.Language
import com.leeturner.wiremock.micronaut.ConfigureWireMock
import com.leeturner.wiremock.micronaut.EnableWireMock
import com.leeturner.wiremock.micronaut.InjectWireMock
import io.micronaut.test.extensions.junit5.annotation.MicronautTest
import jakarta.inject.Inject
import org.junit.jupiter.api.Test
import strikt.api.expectThat
import strikt.arrow.isLeft
import strikt.arrow.isRight
import strikt.assertions.isEqualTo
import strikt.assertions.isNull
import strikt.assertions.isTrue

@MicronautTest
@EnableWireMock(
    ConfigureWireMock(
        name = "micronaut-launch",
        baseUrlProperties = ["micronaut.http.services.micronaut-launch.url"],
    ),
)
class MicronautLaunchFeatureRetrieverTest {
    @InjectWireMock("micronaut-launch")
    private lateinit var wireMock: WireMockServer

    @Inject
    lateinit var retriever: MicronautLaunchFeatureRetriever

    private val type = ApplicationType(title = "", name = "", description = "", value = "DEFAULT", label = "")
    private val language = Language(extension = "", description = "", name = "", value = "JAVA", label = "")

    @Test
    fun `200 maps launch features`() {
        stubFeatures(okJson("/payloads/get-features-default-java.json"))

        expectThat(retriever.getFeatures(type, language)).isRight().get { value }.and {
            get { first { it.name == "data-jdbc" } }.isEqualTo(
                Feature(
                    name = "data-jdbc",
                    title = "Micronaut Data JDBC",
                    description = "Adds support for Micronaut Data JDBC",
                    category = "Database",
                    preview = false,
                    community = false,
                ),
            )
            get { first { it.name == "buildless" }.community }.isTrue()
            get { first { it.name == "control-panel" }.preview }.isTrue()
        }
    }

    @Test
    fun `missing fields are defaulted and nameless features skipped`() {
        stubFeatures(okJson("/payloads/get-features-missing-fields.json"))

        expectThat(retriever.getFeatures(type, language))
            .isRight()
            .get { value }
            .isEqualTo(listOf(Feature("bare", "", "", "Other", preview = false, community = false)))
    }

    @Test
    fun `500 is an error with its status`() {
        stubFeatures(WireMock.aResponse().withStatus(500))

        expectThat(retriever.getFeatures(type, language)).isLeft().get { value.status }.isEqualTo(500)
    }

    @Test
    fun `connection failure is an error`() {
        stubFeatures(WireMock.aResponse().withFault(Fault.CONNECTION_RESET_BY_PEER))

        expectThat(retriever.getFeatures(type, language)).isLeft().get { value.status }.isNull()
    }

    @Test
    fun `language unknown to the client is an error`() {
        expectThat(retriever.getFeatures(type, language.copy(value = "COBOL")))
            .isLeft()
            .get { value }
            .isEqualTo(FeaturesError(null, "Unsupported language: COBOL"))
    }

    private fun okJson(payload: String) = WireMock.okJson(javaClass.getResource(payload)!!.readText())

    private fun stubFeatures(response: ResponseDefinitionBuilder) {
        wireMock.stubFor(
            WireMock.get(WireMock.urlPathEqualTo("/application-types/DEFAULT/features/JAVA")).willReturn(response),
        )
    }
}

package com.leeturner.mtui.adapters.outbound.http

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder
import com.github.tomakehurst.wiremock.client.WireMock
import com.github.tomakehurst.wiremock.http.Fault
import com.leeturner.mtui.domain.core.model.ApplicationType
import com.leeturner.mtui.domain.core.model.BuildType
import com.leeturner.mtui.domain.core.model.JdkVersion
import com.leeturner.mtui.domain.core.model.Language
import com.leeturner.mtui.domain.core.model.ProjectName
import com.leeturner.mtui.domain.core.model.ProjectOptions
import com.leeturner.mtui.domain.core.model.ProjectRejected
import com.leeturner.mtui.domain.core.model.TestFramework
import com.leeturner.mtui.domain.core.model.UnexpectedProjectCreationError
import com.leeturner.wiremock.micronaut.ConfigureWireMock
import com.leeturner.wiremock.micronaut.EnableWireMock
import com.leeturner.wiremock.micronaut.InjectWireMock
import io.micronaut.test.extensions.junit5.annotation.MicronautTest
import jakarta.inject.Inject
import org.junit.jupiter.api.Test
import strikt.api.expectThat
import strikt.arrow.isLeft
import strikt.arrow.isRight
import strikt.assertions.contains
import strikt.assertions.isA
import strikt.assertions.isEqualTo
import strikt.assertions.isNull

@MicronautTest
@EnableWireMock(
    ConfigureWireMock(
        name = "micronaut-launch",
        baseUrlProperties = ["micronaut.http.services.micronaut-launch.url"],
    ),
)
class MicronautLaunchProjectCreatorTest {
    @InjectWireMock("micronaut-launch")
    private lateinit var wireMock: WireMockServer

    @Inject
    lateinit var creator: MicronautLaunchProjectCreator

    private val options =
        ProjectOptions(
            type = ApplicationType(title = "", name = "", description = "", value = "DEFAULT", label = ""),
            language = Language(extension = "", description = "", name = "", value = "KOTLIN", label = ""),
            build = BuildType(description = "", value = "GRADLE_KOTLIN", label = ""),
            test = TestFramework(description = "", name = "", value = "KOTEST", label = ""),
            jdk = JdkVersion(description = "", name = "", value = "JDK_21", label = ""),
        )

    @Test
    fun `201 returns the zip bytes`() {
        val zip = byteArrayOf(0x50, 0x4B, 0x03, 0x04)
        stubCreate(
            "com.example.my-app",
            WireMock
                .aResponse()
                .withStatus(201)
                .withHeader("Content-Type", "application/zip")
                .withBody(zip),
        )

        expectThat(creator.createProject(options, name("com.example.my-app"), emptyList()))
            .isRight()
            .get { value.toList() }
            .isEqualTo(zip.toList())
    }

    @Test
    fun `400 surfaces launch's message`() {
        stubCreate(
            "MyApp",
            WireMock
                .aResponse()
                .withStatus(400)
                .withHeader("Content-Type", "application/json")
                .withBody(javaClass.getResource("/payloads/create-app-invalid-name.json")!!.readText()),
        )

        expectThat(creator.createProject(options, name("MyApp"), emptyList()))
            .isLeft()
            .get { value }
            .isA<ProjectRejected>()
            .get { message }
            .isEqualTo("Invalid package name: MyApp")
    }

    @Test
    fun `400 with an unreadable body falls back to the status text`() {
        stubCreate(
            "MyApp",
            WireMock
                .aResponse()
                .withStatus(400)
                .withHeader("Content-Type", "text/html")
                .withBody("<html>nope</html>"),
        )

        expectThat(creator.createProject(options, name("MyApp"), emptyList()))
            .isLeft()
            .get { value }
            .isA<ProjectRejected>()
            .get { message }
            .isEqualTo("Bad Request")
    }

    @Test
    fun `connection failure is an unexpected error`() {
        stubCreate("com.example.my-app", WireMock.aResponse().withFault(Fault.CONNECTION_RESET_BY_PEER))

        expectThat(creator.createProject(options, name("com.example.my-app"), emptyList()))
            .isLeft()
            .get { value }
            .isA<UnexpectedProjectCreationError>()
            .get { status }
            .isNull()
    }

    @Test
    fun `500 is an unexpected error with its status`() {
        stubCreate("com.example.my-app", WireMock.aResponse().withStatus(500))

        expectThat(creator.createProject(options, name("com.example.my-app"), emptyList()))
            .isLeft()
            .get { value }
            .isA<UnexpectedProjectCreationError>()
            .get { status }
            .isEqualTo(500)
    }

    @Test
    fun `application type unknown to the client is an unexpected error`() {
        val unknown = options.copy(type = options.type.copy(value = "SERVERLESS"))

        expectThat(creator.createProject(unknown, name("com.example.my-app"), emptyList()))
            .isLeft()
            .get { value }
            .isA<UnexpectedProjectCreationError>()
            .get { message }
            .isEqualTo("Unsupported application type: SERVERLESS")
    }

    @Test
    fun `selected features are sent to launch`() {
        stubCreate("com.example.my-app", WireMock.aResponse().withStatus(201).withBody(byteArrayOf(1)))

        creator.createProject(options, name("com.example.my-app"), listOf("data-jdbc", "flyway"))

        expectThat(
            wireMock.allServeEvents
                .single()
                .request.url,
        ).contains("data-jdbc").contains("flyway")
    }

    @Test
    fun `400 for clashing features surfaces launch's message`() {
        stubCreate(
            "com.example.my-app",
            WireMock
                .aResponse()
                .withStatus(400)
                .withHeader("Content-Type", "application/json")
                .withBody(javaClass.getResource("/payloads/create-app-clashing-features.json")!!.readText()),
        )

        expectThat(creator.createProject(options, name("com.example.my-app"), listOf("data-jdbc", "data-jpa")))
            .isLeft()
            .get { value }
            .isA<ProjectRejected>()
            .get { message }
            .isEqualTo("There can only be one of the following features selected: [data-jdbc, data-jpa]")
    }

    @Test
    fun `chosen options are sent to launch`() {
        stubCreate("com.example.my-app", WireMock.aResponse().withStatus(201).withBody(byteArrayOf(1)))

        creator.createProject(options, name("com.example.my-app"), emptyList())

        wireMock.verify(
            WireMock
                .getRequestedFor(WireMock.urlPathEqualTo("/create/DEFAULT/com.example.my-app"))
                .withQueryParam("lang", WireMock.equalTo("KOTLIN"))
                .withQueryParam("build", WireMock.equalTo("GRADLE_KOTLIN"))
                .withQueryParam("test", WireMock.equalTo("KOTEST"))
                .withQueryParam("javaVersion", WireMock.equalTo("JDK_21")),
        )
    }

    @Test
    fun `language unknown to the client is an unexpected error`() {
        val unknown = options.copy(language = options.language.copy(value = "PYTHON2"))

        expectUnsupported(unknown, "Unsupported language: PYTHON2")
    }

    @Test
    fun `test framework unknown to the client is an unexpected error`() {
        val unknown = options.copy(test = options.test.copy(value = "TESTNG"))

        expectUnsupported(unknown, "Unsupported test framework: TESTNG")
    }

    @Test
    fun `build type unknown to the client is an unexpected error`() {
        val unknown = options.copy(build = options.build.copy(value = "ANT"))

        expectUnsupported(unknown, "Unsupported build type: ANT")
    }

    private fun expectUnsupported(
        unknown: ProjectOptions,
        message: String,
    ) {
        expectThat(creator.createProject(unknown, name("com.example.my-app"), emptyList()))
            .isLeft()
            .get { value }
            .isA<UnexpectedProjectCreationError>()
            .get { this.message }
            .isEqualTo(message)
    }

    private fun name(raw: String) = ProjectName.parse(raw).getOrNull()!!

    private fun stubCreate(
        name: String,
        response: ResponseDefinitionBuilder,
    ) {
        wireMock.stubFor(WireMock.get(WireMock.urlPathEqualTo("/create/DEFAULT/$name")).willReturn(response))
    }
}

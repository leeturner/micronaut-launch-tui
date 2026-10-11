package com.leeturner.mtui.adapters.outbound.http

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder
import com.github.tomakehurst.wiremock.client.WireMock
import com.github.tomakehurst.wiremock.http.Fault
import com.leeturner.mtui.domain.core.model.ApplicationType
import com.leeturner.mtui.domain.core.model.BuildType
import com.leeturner.mtui.domain.core.model.JdkVersion
import com.leeturner.mtui.domain.core.model.Language
import com.leeturner.mtui.domain.core.model.LaunchServer
import com.leeturner.mtui.domain.core.model.MicronautVersion
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
import strikt.assertions.doesNotContain
import strikt.assertions.isA
import strikt.assertions.isEmpty
import strikt.assertions.isEqualTo
import strikt.assertions.isNull
import strikt.assertions.startsWith

@MicronautTest
@EnableWireMock(
    ConfigureWireMock(
        name = "micronaut-launch",
        baseUrlProperties = ["micronaut.http.services.micronaut-launch.url"],
    ),
    ConfigureWireMock(name = "snapshot", baseUrlProperties = ["mtui.launch.snapshot-url"]),
)
class MicronautLaunchProjectCreatorTest {
    @InjectWireMock("micronaut-launch")
    private lateinit var wireMock: WireMockServer

    @InjectWireMock("snapshot")
    private lateinit var snapshot: WireMockServer

    @Inject
    lateinit var creator: MicronautLaunchProjectCreator

    private val options =
        ProjectOptions(
            version = MicronautVersion(LaunchServer.RELEASE, "5.2.2"),
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
    }

    @Test
    fun `500 is an unexpected error`() {
        stubCreate("com.example.my-app", WireMock.aResponse().withStatus(500))

        expectThat(creator.createProject(options, name("com.example.my-app"), emptyList()))
            .isLeft()
            .get { value }
            .isA<UnexpectedProjectCreationError>()
            .get { message }
            .isEqualTo("Client 'micronaut-launch': Server Error")
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

    @Test
    fun `snapshot server is called when it is chosen`() {
        val zip = byteArrayOf(0x50, 0x4B, 0x03, 0x04)
        stubCreate("com.example.my-app", WireMock.aResponse().withStatus(201).withBody(zip), on = snapshot)
        val onSnapshot = options.copy(version = MicronautVersion(LaunchServer.SNAPSHOT, "5.2.3-SNAPSHOT"))

        expectThat(creator.createProject(onSnapshot, name("com.example.my-app"), emptyList()))
            .isRight()
            .get { value.toList() }
            .isEqualTo(zip.toList())
        expectThat(wireMock.allServeEvents).isEmpty()
    }

    @Test
    fun `preview returns the text files and drops binary ones`() {
        stubPreview(
            WireMock
                .aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody(javaClass.getResource("/payloads/preview-app.json")!!.readText()),
        )

        val preview = creator.previewProject(options, name("com.example.demo"), emptyList())

        expectThat(preview)
            .isRight()
            .get { value.files.map { it.path } }
            .doesNotContain("gradle/wrapper/gradle-wrapper.jar")
        expectThat(preview)
            .isRight()
            .get { value.files[value.buildFileIndex].content }
            .startsWith("plugins {")
    }

    @Test
    fun `preview sends the chosen options`() {
        stubPreview(
            WireMock
                .aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody("""{"contents": {"a": "b"}}"""),
        )

        creator.previewProject(options, name("com.example.demo"), listOf("data-jdbc"))

        wireMock.verify(
            WireMock
                .getRequestedFor(WireMock.urlPathEqualTo("/preview/DEFAULT/com.example.demo"))
                .withQueryParam("lang", WireMock.equalTo("KOTLIN"))
                .withQueryParam("build", WireMock.equalTo("GRADLE_KOTLIN"))
                .withQueryParam("test", WireMock.equalTo("KOTEST"))
                .withQueryParam("javaVersion", WireMock.equalTo("JDK_21"))
                .withQueryParam("features", WireMock.equalTo("data-jdbc")),
        )
    }

    @Test
    fun `preview 400 surfaces launch's message`() {
        stubPreview(
            WireMock
                .aResponse()
                .withStatus(400)
                .withHeader("Content-Type", "application/json")
                .withBody(javaClass.getResource("/payloads/create-app-invalid-name.json")!!.readText()),
            "MyApp",
        )

        expectThat(creator.previewProject(options, name("MyApp"), emptyList()))
            .isLeft()
            .get { value }
            .isA<ProjectRejected>()
            .get { message }
            .isEqualTo("Invalid package name: MyApp")
    }

    @Test
    fun `preview with no text files is an error`() {
        stubPreview(
            WireMock
                .aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody("""{"contents": {"a.jar": null}}"""),
        )

        expectThat(creator.previewProject(options, name("com.example.demo"), emptyList()))
            .isLeft()
            .get { value }
            .isA<UnexpectedProjectCreationError>()
            .get { message }
            .isEqualTo("Launch returned no files to preview")
    }

    @Test
    fun `preview goes to the snapshot server when it is chosen`() {
        stubPreview(
            WireMock
                .aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody("""{"contents": {"a": "b"}}"""),
            on = snapshot,
        )
        val onSnapshot = options.copy(version = MicronautVersion(LaunchServer.SNAPSHOT, "5.2.3-SNAPSHOT"))

        expectThat(creator.previewProject(onSnapshot, name("com.example.demo"), emptyList())).isRight()
        expectThat(wireMock.allServeEvents).isEmpty()
    }

    private fun stubPreview(
        response: ResponseDefinitionBuilder,
        name: String = "com.example.demo",
        on: WireMockServer = wireMock,
    ) {
        on.stubFor(WireMock.get(WireMock.urlPathEqualTo("/preview/DEFAULT/$name")).willReturn(response))
    }

    private fun name(raw: String) = ProjectName.parse(raw).getOrNull()!!

    private fun stubCreate(
        name: String,
        response: ResponseDefinitionBuilder,
        on: WireMockServer = wireMock,
    ) {
        on.stubFor(WireMock.get(WireMock.urlPathEqualTo("/create/DEFAULT/$name")).willReturn(response))
    }
}

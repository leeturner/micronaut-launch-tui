package com.leeturner.mtui.domain.core.services

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import com.leeturner.mtui.domain.core.model.ApplicationType
import com.leeturner.mtui.domain.core.model.BuildType
import com.leeturner.mtui.domain.core.model.GenerateProjectError
import com.leeturner.mtui.domain.core.model.JdkVersion
import com.leeturner.mtui.domain.core.model.Language
import com.leeturner.mtui.domain.core.model.ProjectAlreadyExists
import com.leeturner.mtui.domain.core.model.ProjectName
import com.leeturner.mtui.domain.core.model.ProjectOptions
import com.leeturner.mtui.domain.core.model.ProjectRejected
import com.leeturner.mtui.domain.core.model.TestFramework
import com.leeturner.mtui.domain.core.ports.ProjectCreator
import com.leeturner.mtui.domain.core.ports.ProjectWriter
import org.junit.jupiter.api.Test
import strikt.api.expectThat
import strikt.arrow.isLeft
import strikt.arrow.isRight
import strikt.assertions.isA
import strikt.assertions.isEqualTo
import java.nio.file.Path

class ProjectGeneratorTest {
    private val options =
        ProjectOptions(
            type = ApplicationType(title = "", name = "", description = "", value = "DEFAULT", label = ""),
            language = Language(extension = "", description = "", name = "", value = "KOTLIN", label = ""),
            build = BuildType(description = "", value = "GRADLE_KOTLIN", label = ""),
            test = TestFramework(description = "", name = "", value = "JUNIT", label = ""),
            jdk = JdkVersion(description = "", name = "", value = "JDK_21", label = ""),
        )
    private val name = ProjectName.parse("com.example.my-app").getOrNull()!!
    private val into = Path.of("/work")
    private val creator = FakeCreator()
    private val writer = FakeWriter()
    private val generator = ProjectGenerator(creator, writer)

    @Test
    fun `existing folder is refused before calling launch`() {
        writer.existing = true

        expectThat(generator.generate(options, name, emptyList(), into))
            .isLeft()
            .get { value }
            .isA<ProjectAlreadyExists>()
            .get { path }
            .isEqualTo(into.resolve("my-app"))
        expectThat(creator.calls).isEqualTo(0)
    }

    @Test
    fun `existing folder message names the folder, not the full path`() {
        writer.existing = true

        expectThat(generator.generate(options, name, emptyList(), into))
            .isLeft()
            .get { value.message }
            .isEqualTo("Folder my-app already exists")
    }

    @Test
    fun `launch errors are passed through and nothing is written`() {
        creator.result = ProjectRejected("Invalid package name: MyApp").left()

        expectThat(generator.generate(options, name, emptyList(), into)).isLeft().get { value }.isA<ProjectRejected>()
        expectThat(writer.writes).isEqualTo(0)
    }

    @Test
    fun `zip from launch is written and the project path returned`() {
        creator.result = byteArrayOf(1, 2).right()
        writer.result = into.resolve("my-app").right()

        expectThat(generator.generate(options, name, emptyList(), into))
            .isRight()
            .get { value }
            .isEqualTo(into.resolve("my-app"))
    }

    @Test
    fun `options and features are passed to launch`() {
        generator.generate(options, name, listOf("data-jdbc"), into)

        expectThat(creator.lastOptions).isEqualTo(options)
        expectThat(creator.lastFeatures).isEqualTo(listOf("data-jdbc"))
    }

    @Test
    fun `checkAvailable refuses an existing folder`() {
        writer.existing = true

        expectThat(generator.checkAvailable(name, into)).isLeft().get { value }.isA<ProjectAlreadyExists>()
    }

    @Test
    fun `checkAvailable accepts a new folder`() {
        expectThat(generator.checkAvailable(name, into)).isRight()
    }

    private class FakeCreator : ProjectCreator {
        var calls = 0
        var lastOptions: ProjectOptions? = null
        var lastFeatures: List<String>? = null
        var result: Either<GenerateProjectError, ByteArray> = byteArrayOf().right()

        override fun createProject(
            options: ProjectOptions,
            name: ProjectName,
            features: List<String>,
        ): Either<GenerateProjectError, ByteArray> {
            calls++
            lastOptions = options
            lastFeatures = features
            return result
        }
    }

    private class FakeWriter : ProjectWriter {
        var existing = false
        var writes = 0
        var result: Either<GenerateProjectError, Path> = Path.of("/work/my-app").right()

        override fun exists(
            name: ProjectName,
            into: Path,
        ) = existing

        override fun write(
            zip: ByteArray,
            into: Path,
        ): Either<GenerateProjectError, Path> {
            writes++
            return result
        }
    }
}

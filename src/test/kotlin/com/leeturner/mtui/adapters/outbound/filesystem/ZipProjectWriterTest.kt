package com.leeturner.mtui.adapters.outbound.filesystem

import com.leeturner.mtui.domain.core.model.ProjectAlreadyExists
import com.leeturner.mtui.domain.core.model.ProjectName
import com.leeturner.mtui.domain.core.model.ProjectWriteFailed
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import strikt.api.expectThat
import strikt.arrow.isLeft
import strikt.arrow.isRight
import strikt.assertions.isA
import strikt.assertions.isEmpty
import strikt.assertions.isEqualTo
import strikt.assertions.isFalse
import strikt.assertions.isTrue
import java.io.ByteArrayOutputStream
import java.nio.file.Files
import java.nio.file.Path
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.io.path.createDirectory
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.readText
import kotlin.io.path.writeText

class ZipProjectWriterTest {
    @TempDir
    lateinit var into: Path

    private val writer = ZipProjectWriter()

    @Test
    fun `extracts into a new top-level folder`() {
        val zip =
            zipOf(
                "my-app/" to "",
                "my-app/build.gradle.kts" to "plugins {}",
                "my-app/src/main/App.kt" to "fun main() {}",
            )

        expectThat(writer.write(zip, into))
            .isRight()
            .get { value }
            .isEqualTo(into.resolve("my-app").toAbsolutePath())
        expectThat(into.resolve("my-app/src/main/App.kt").readText()).isEqualTo("fun main() {}")
    }

    @Test
    fun `wrapper scripts are executable`() {
        val zip = zipOf("my-app/gradlew" to "#!/bin/sh", "my-app/mvnw" to "#!/bin/sh", "my-app/README.md" to "")

        writer.write(zip, into)

        expectThat(Files.isExecutable(into.resolve("my-app/gradlew"))).isTrue()
        expectThat(Files.isExecutable(into.resolve("my-app/mvnw"))).isTrue()
        expectThat(Files.isExecutable(into.resolve("my-app/README.md"))).isFalse()
    }

    @Test
    fun `an entry escaping the target rejects the zip and writes nothing`() {
        val zip = zipOf("my-app/ok.txt" to "", "my-app/../../evil.txt" to "")

        expectThat(writer.write(zip, into)).isLeft().get { value }.isA<ProjectWriteFailed>()
        expectThat(into.listDirectoryEntries()).isEmpty()
    }

    @Test
    fun `more than one top-level folder rejects the zip and writes nothing`() {
        val zip = zipOf("my-app/a.txt" to "", "other/b.txt" to "")

        expectThat(writer.write(zip, into)).isLeft().get { value }.isA<ProjectWriteFailed>()
        expectThat(into.listDirectoryEntries()).isEmpty()
    }

    @Test
    fun `an existing folder is refused and left untouched`() {
        into
            .resolve("my-app")
            .createDirectory()
            .resolve("keep.txt")
            .writeText("mine")
        val zip = zipOf("my-app/keep.txt" to "theirs")

        expectThat(writer.write(zip, into)).isLeft().get { value }.isA<ProjectAlreadyExists>()
        expectThat(into.resolve("my-app/keep.txt").readText()).isEqualTo("mine")
    }

    @Test
    fun `a failure midway removes the half-written folder`() {
        // "my-app/a" is written as a file, so creating "my-app/a/b" under it fails
        val zip = zipOf("my-app/a" to "file", "my-app/a/b" to "boom")

        expectThat(writer.write(zip, into)).isLeft().get { value }.isA<ProjectWriteFailed>()
        expectThat(into.listDirectoryEntries()).isEmpty()
    }

    @Test
    fun `exists checks the name's folder`() {
        into.resolve("my-app").createDirectory()

        expectThat(writer.exists(ProjectName.parse("com.example.my-app").getOrNull()!!, into)).isTrue()
        expectThat(writer.exists(ProjectName.parse("com.example.other").getOrNull()!!, into)).isFalse()
    }

    private fun zipOf(vararg entries: Pair<String, String>): ByteArray {
        val bytes = ByteArrayOutputStream()
        ZipOutputStream(bytes).use { zip ->
            entries.forEach { (name, content) ->
                zip.putNextEntry(ZipEntry(name))
                zip.write(content.toByteArray())
                zip.closeEntry()
            }
        }
        return bytes.toByteArray()
    }
}

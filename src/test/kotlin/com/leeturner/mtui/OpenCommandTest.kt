package com.leeturner.mtui

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import strikt.api.expectThat
import strikt.arrow.isLeft
import strikt.arrow.isRight
import strikt.assertions.contains
import strikt.assertions.isEqualTo
import strikt.assertions.isNull
import java.nio.file.Files
import java.nio.file.Path

class OpenCommandTest {
    @TempDir
    lateinit var dir: Path

    @Test
    fun `the option wins over the environment`() {
        expectThat(openCommand("code -n", "idea")).isEqualTo(listOf("code", "-n"))
    }

    @Test
    fun `falls back to the environment`() {
        expectThat(openCommand(null, "idea")).isEqualTo(listOf("idea"))
    }

    @Test
    fun `neither set means no command`() {
        expectThat(openCommand(null, null)).isNull()
    }

    @Test
    fun `an empty option turns opening off`() {
        expectThat(openCommand("", "idea")).isNull()
    }

    @Test
    fun `blank means no command`() {
        expectThat(openCommand(null, "   ")).isNull()
    }

    @Test
    fun `splits on runs of whitespace and trims`() {
        expectThat(openCommand("  code   -n ", null)).isEqualTo(listOf("code", "-n"))
    }

    // `test -d <path>` succeeds only when the path is passed as the last argument and is a directory
    @Test
    fun `runs the command with the project path`() {
        expectThat(openProject(listOf("test", "-d"), dir)).isRight()
    }

    @Test
    fun `a path that is not a directory fails the check`() {
        expectThat(openProject(listOf("test", "-d"), dir.resolve("missing"))).isLeft()
    }

    @Test
    fun `a path with a space is one argument`() {
        val project = Files.createDirectory(dir.resolve("my demo"))

        expectThat(openProject(listOf("test", "-d"), project)).isRight()
    }

    @Test
    fun `a non-zero exit is a failure`() {
        expectThat(openProject(listOf("false"), dir)).isLeft().get { value }.isEqualTo("it exited with code 1")
    }

    @Test
    fun `a missing program is a failure`() {
        expectThat(openProject(listOf("mtui-no-such-command"), dir))
            .isLeft()
            .get { value }
            .contains("mtui-no-such-command")
    }
}

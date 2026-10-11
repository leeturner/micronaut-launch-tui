package com.leeturner.mtui

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import java.io.IOException
import java.nio.file.Path

// The option wins over the environment, so an empty option turns opening off; blank means no command.
// Split on whitespace only, so arguments can't contain quotes or spaces
internal fun openCommand(
    option: String?,
    env: String?,
): List<String>? = (option ?: env)?.trim()?.takeIf { it.isNotEmpty() }?.split(Regex("\\s+"))

// Runs the command with the project path as its last argument, in this terminal, and waits for it
internal fun openProject(
    command: List<String>,
    path: Path,
): Either<String, Unit> =
    try {
        val code = ProcessBuilder(command + path.toString()).inheritIO().start().waitFor()
        if (code == 0) Unit.right() else "it exited with code $code".left()
    } catch (e: IOException) {
        (e.message ?: e.toString()).left()
    }

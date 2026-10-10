package com.leeturner.mtui.adapters.outbound.filesystem

import arrow.core.Either
import arrow.core.raise.Raise
import arrow.core.raise.either
import arrow.core.raise.ensure
import arrow.core.raise.ensureNotNull
import com.leeturner.mtui.domain.core.model.GenerateProjectError
import com.leeturner.mtui.domain.core.model.ProjectAlreadyExists
import com.leeturner.mtui.domain.core.model.ProjectName
import com.leeturner.mtui.domain.core.model.ProjectWriteFailed
import com.leeturner.mtui.domain.core.ports.ProjectWriter
import jakarta.inject.Singleton
import java.io.IOException
import java.nio.file.FileAlreadyExistsException
import java.nio.file.Files
import java.nio.file.Path
import java.util.zip.ZipInputStream
import kotlin.io.path.exists

@Singleton
class ZipProjectWriter : ProjectWriter {
    override fun exists(
        name: ProjectName,
        into: Path,
    ): Boolean = into.resolve(name.folderName).exists()

    override fun write(
        zip: ByteArray,
        into: Path,
    ): Either<GenerateProjectError, Path> =
        either {
            // Read and check everything first so a bad zip never touches the disk
            val entries = readEntries(zip)
            val target = into.resolve(topLevelFolder(entries)).toAbsolutePath().normalize()
            entries.forEach { entry ->
                ensure(target.resolve(entry.name).normalize().startsWith(target)) {
                    ProjectWriteFailed("Zip entry ${entry.name} escapes the project folder")
                }
            }

            try {
                Files.createDirectory(target)
            } catch (_: FileAlreadyExistsException) {
                raise(ProjectAlreadyExists(target))
            } catch (e: IOException) {
                raise(ProjectWriteFailed(e.message ?: "Could not create $target"))
            }

            try {
                entries.forEach { it.writeTo(into) }
            } catch (e: IOException) {
                // The folder didn't exist before this write, so removing it loses nothing of the user's
                target.toFile().deleteRecursively()
                raise(ProjectWriteFailed(e.message ?: "Could not write $target"))
            }
            target
        }

    private fun Raise<GenerateProjectError>.readEntries(zip: ByteArray): List<Entry> =
        try {
            ZipInputStream(zip.inputStream()).use { stream ->
                generateSequence { stream.nextEntry }
                    .map { Entry(it.name, it.isDirectory, stream.readBytes()) }
                    .toList()
            }
        } catch (e: IOException) {
            raise(ProjectWriteFailed("Could not read the project zip: ${e.message}"))
        }

    private fun Raise<GenerateProjectError>.topLevelFolder(entries: List<Entry>): String {
        val folders = entries.map { it.name.substringBefore('/') }.toSet()
        val folder =
            folders
                .singleOrNull()
                ?.takeIf { it !in setOf("", ".", "..") && entries.all { entry -> '/' in entry.name } }
        return ensureNotNull(folder) {
            ProjectWriteFailed("Expected the project zip to contain one top-level folder, found $folders")
        }
    }

    private class Entry(
        val name: String,
        val isDirectory: Boolean,
        val bytes: ByteArray,
    ) {
        fun writeTo(into: Path) {
            val path = into.resolve(name)
            if (isDirectory) {
                Files.createDirectories(path)
                return
            }
            Files.createDirectories(path.parent)
            Files.write(path, bytes)
            // ponytail: java.util.zip drops Unix modes, so only gradlew/mvnw get +x (the only executables Launch
            // ships as of 5.2.2); read modes via zipfs "enablePosixFileAttributes" if that changes
            if (path.fileName.toString() in wrapperScripts) path.toFile().setExecutable(true, false)
        }
    }

    private companion object {
        val wrapperScripts = setOf("gradlew", "mvnw")
    }
}

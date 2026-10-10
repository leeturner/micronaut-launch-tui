package com.leeturner.mtui.domain.core.services

import arrow.core.Either
import arrow.core.raise.either
import arrow.core.raise.ensure
import com.leeturner.mtui.domain.core.model.ApplicationType
import com.leeturner.mtui.domain.core.model.GenerateProjectError
import com.leeturner.mtui.domain.core.model.ProjectAlreadyExists
import com.leeturner.mtui.domain.core.model.ProjectName
import com.leeturner.mtui.domain.core.ports.ProjectCreator
import com.leeturner.mtui.domain.core.ports.ProjectWriter
import jakarta.inject.Singleton
import java.nio.file.Path

@Singleton
class ProjectGenerator(
    private val creator: ProjectCreator,
    private val writer: ProjectWriter,
) {
    fun generate(
        type: ApplicationType,
        name: ProjectName,
        features: List<String>,
        into: Path,
    ): Either<GenerateProjectError, Path> =
        either {
            // Checked before calling Launch so an existing folder fails fast; the writer checks again atomically
            checkAvailable(name, into).bind()
            val zip = creator.createProject(type, name, features).bind()
            writer.write(zip, into).bind()
        }

    fun checkAvailable(
        name: ProjectName,
        into: Path,
    ): Either<GenerateProjectError, Unit> =
        either {
            ensure(!writer.exists(name, into)) { ProjectAlreadyExists(into.resolve(name.folderName)) }
        }
}

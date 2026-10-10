package com.leeturner.mtui.domain.core.ports

import arrow.core.Either
import com.leeturner.mtui.domain.core.model.GenerateProjectError
import com.leeturner.mtui.domain.core.model.ProjectName
import java.nio.file.Path

interface ProjectWriter {
    fun exists(
        name: ProjectName,
        into: Path,
    ): Boolean

    // Returns the absolute path of the created project folder
    fun write(
        zip: ByteArray,
        into: Path,
    ): Either<GenerateProjectError, Path>
}

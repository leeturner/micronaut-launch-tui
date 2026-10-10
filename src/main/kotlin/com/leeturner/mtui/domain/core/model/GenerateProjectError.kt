package com.leeturner.mtui.domain.core.model

import java.nio.file.Path

sealed interface GenerateProjectError {
    val message: String
}

data class ProjectAlreadyExists(
    val path: Path,
) : GenerateProjectError {
    override val message: String get() = "$path already exists"
}

data class ProjectRejected(
    override val message: String,
) : GenerateProjectError

data class UnexpectedProjectCreationError(
    val status: Int?,
    override val message: String,
) : GenerateProjectError

data class ProjectWriteFailed(
    override val message: String,
) : GenerateProjectError

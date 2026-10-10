package com.leeturner.mtui.domain.core.ports

import arrow.core.Either
import com.leeturner.mtui.domain.core.model.GenerateProjectError
import com.leeturner.mtui.domain.core.model.ProjectName
import com.leeturner.mtui.domain.core.model.ProjectOptions

interface ProjectCreator {
    fun createProject(
        options: ProjectOptions,
        name: ProjectName,
        features: List<String>,
    ): Either<GenerateProjectError, ByteArray>
}

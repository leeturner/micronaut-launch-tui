package com.leeturner.mtui.domain.core.ports

import arrow.core.Either
import com.leeturner.mtui.domain.core.model.ApplicationType
import com.leeturner.mtui.domain.core.model.GenerateProjectError
import com.leeturner.mtui.domain.core.model.ProjectName

interface ProjectCreator {
    fun createProject(
        type: ApplicationType,
        name: ProjectName,
        features: List<String>,
    ): Either<GenerateProjectError, ByteArray>
}

package com.leeturner.mtui.domain.core.ports

import arrow.core.Either
import com.leeturner.mtui.domain.core.model.LaunchServer
import com.leeturner.mtui.domain.core.model.VersionError

interface VersionRetriever {
    fun getMicronautVersion(server: LaunchServer): Either<VersionError, String>
}

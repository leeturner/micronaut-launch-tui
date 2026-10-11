package com.leeturner.mtui.domain.core.ports

import arrow.core.Either
import com.leeturner.mtui.domain.core.model.LaunchServer
import com.leeturner.mtui.domain.core.model.SelectOptions
import com.leeturner.mtui.domain.core.model.SelectOptionsError

interface SelectOptionRetriever {
    fun getSelectOptions(server: LaunchServer): Either<SelectOptionsError, SelectOptions>
}

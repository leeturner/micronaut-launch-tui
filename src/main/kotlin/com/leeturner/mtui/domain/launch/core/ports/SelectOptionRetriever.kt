package com.leeturner.mtui.domain.launch.core.ports

import arrow.core.Either
import com.leeturner.mtui.domain.launch.core.model.SelectOptions
import com.leeturner.mtui.domain.launch.core.model.SelectOptionsError

interface SelectOptionRetriever {
    fun getSelectOptions(): Either<SelectOptionsError, SelectOptions>
}

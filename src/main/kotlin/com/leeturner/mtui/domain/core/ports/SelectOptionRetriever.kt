package com.leeturner.mtui.domain.core.ports

import arrow.core.Either
import com.leeturner.mtui.domain.core.model.SelectOptions
import com.leeturner.mtui.domain.core.model.SelectOptionsError

interface SelectOptionRetriever {
    fun getSelectOptions(): Either<SelectOptionsError, SelectOptions>
}

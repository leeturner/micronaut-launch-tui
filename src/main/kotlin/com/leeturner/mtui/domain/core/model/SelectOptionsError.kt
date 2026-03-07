package com.leeturner.mtui.domain.core.model

sealed interface SelectOptionsError

data class UnexpectedSelectOptionRetrievalError(
    val status: Int,
    val message: String,
) : SelectOptionsError

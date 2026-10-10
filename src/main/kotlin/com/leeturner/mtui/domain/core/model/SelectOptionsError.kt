package com.leeturner.mtui.domain.core.model

sealed interface SelectOptionsError {
    val message: String
}

data class UnexpectedSelectOptionRetrievalError(
    val status: Int?,
    override val message: String,
) : SelectOptionsError

data class EmptySelectOptionsError(
    override val message: String,
) : SelectOptionsError

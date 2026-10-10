package com.leeturner.mtui.domain.core.model

import arrow.core.Either
import arrow.core.left
import arrow.core.right

class ProjectName private constructor(
    val value: String,
) {
    val folderName: String get() = value.substringAfterLast('.')

    override fun equals(other: Any?) = other is ProjectName && other.value == value

    override fun hashCode() = value.hashCode()

    override fun toString() = value

    companion object {
        // Launch answers 404 "Page Not Found" for anything outside this set, so it is rejected up front
        private val allowedCharacters = Regex("[A-Za-z0-9._-]+")

        fun parse(raw: String): Either<InvalidProjectName, ProjectName> {
            val value = raw.trim()
            return when {
                value.isEmpty() -> {
                    InvalidProjectName("Name required").left()
                }

                !allowedCharacters.matches(value) -> {
                    InvalidProjectName("Only letters, digits, '.', '-' and '_' are allowed").left()
                }

                value.split('.').any { it.isEmpty() } -> {
                    InvalidProjectName("Name can't start or end with '.' or contain '..'").left()
                }

                else -> {
                    ProjectName(value).right()
                }
            }
        }
    }
}

data class InvalidProjectName(
    val message: String,
)

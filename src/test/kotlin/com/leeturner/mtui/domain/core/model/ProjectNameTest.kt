package com.leeturner.mtui.domain.core.model

import org.junit.jupiter.api.Test
import strikt.api.expect
import strikt.api.expectThat
import strikt.arrow.isLeft
import strikt.arrow.isRight
import strikt.assertions.isEqualTo

class ProjectNameTest {
    @Test
    fun `last segment is the folder name`() {
        expectThat(ProjectName.parse("com.example.my-app")).isRight().and {
            get { value.value }.isEqualTo("com.example.my-app")
            get { value.folderName }.isEqualTo("my-app")
        }
    }

    @Test
    fun `a name without a package is its own folder name`() {
        expectThat(ProjectName.parse("my-app")).isRight().get { value.folderName }.isEqualTo("my-app")
    }

    @Test
    fun `surrounding whitespace is trimmed`() {
        expectThat(ProjectName.parse(" com.example.my-app\n"))
            .isRight()
            .get { value.value }
            .isEqualTo("com.example.my-app")
    }

    @Test
    fun `blank name is rejected`() {
        expectThat(ProjectName.parse("  ")).isLeft().get { value.message }.isEqualTo("Name required")
    }

    @Test
    fun `characters launch cannot route are rejected`() {
        expect {
            listOf("My App", "my-app!", "com/example/app", "café").forEach { raw ->
                that(ProjectName.parse(raw))
                    .describedAs(raw)
                    .isLeft()
                    .get { value.message }
                    .isEqualTo("Only letters, digits, '.', '-' and '_' are allowed")
            }
        }
    }

    @Test
    fun `empty segments are rejected`() {
        expect {
            listOf("com.example.", ".my-app", "com..app").forEach { raw ->
                that(ProjectName.parse(raw))
                    .describedAs(raw)
                    .isLeft()
                    .get { value.message }
                    .isEqualTo("Name can't start or end with '.' or contain '..'")
            }
        }
    }
}

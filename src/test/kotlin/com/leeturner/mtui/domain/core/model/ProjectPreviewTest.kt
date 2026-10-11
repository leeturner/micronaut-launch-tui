package com.leeturner.mtui.domain.core.model

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import strikt.api.expectThat
import strikt.assertions.isEqualTo

class ProjectPreviewTest {
    private fun preview(vararg paths: String) = ProjectPreview(paths.map { ProjectFile(it, "") })

    @Test
    fun `files are in plain path order`() {
        expectThat(preview("README.md", ".gitignore", "build.gradle.kts").files.map { it.path })
            .isEqualTo(listOf(".gitignore", "README.md", "build.gradle.kts"))
    }

    @Test
    fun `the build file is found for each build tool`() {
        for (build in listOf("build.gradle", "build.gradle.kts", "pom.xml")) {
            val preview = preview("README.md", build, "settings.gradle")

            expectThat(preview.files[preview.buildFileIndex].path).isEqualTo(build)
        }
    }

    @Test
    fun `a nested build file doesn't count`() {
        expectThat(preview("sub/build.gradle").buildFileIndex).isEqualTo(0)
    }

    @Test
    fun `a preview needs at least one file`() {
        assertThrows<IllegalArgumentException> { ProjectPreview(emptyList()) }
    }
}

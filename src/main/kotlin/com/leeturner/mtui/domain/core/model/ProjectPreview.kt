package com.leeturner.mtui.domain.core.model

data class ProjectFile(
    val path: String,
    val content: String,
)

class ProjectPreview(
    files: Collection<ProjectFile>,
) {
    // Plain, case-sensitive path order
    val files: List<ProjectFile> = files.sortedBy { it.path }

    // Index of the root build file; 0 when there is none
    val buildFileIndex: Int = this.files.indexOfFirst { it.path in BUILD_FILES }.coerceAtLeast(0)

    init {
        require(files.isNotEmpty()) { "A preview needs at least one file" }
    }

    private companion object {
        val BUILD_FILES = setOf("build.gradle", "build.gradle.kts", "pom.xml")
    }
}

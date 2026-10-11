package com.leeturner.mtui.domain.core.model

// The Launch servers mtui can create projects with; the label is used when the server's version can't be read
enum class LaunchServer(
    val fallbackLabel: String,
) {
    RELEASE("Release"),
    SNAPSHOT("Snapshot"),
}

data class MicronautVersion(
    val server: LaunchServer,
    val label: String,
) {
    companion object {
        fun fallback(server: LaunchServer): MicronautVersion = MicronautVersion(server, server.fallbackLabel)
    }
}

data class VersionError(
    val status: Int?,
    val message: String,
)

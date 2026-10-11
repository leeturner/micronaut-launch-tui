package com.leeturner.mtui.adapters.inbound.tui

import com.leeturner.mtui.domain.core.model.ProjectFile
import com.leeturner.mtui.domain.core.model.ProjectPreview

// Which file is open, how far it is scrolled and the file list's filter, kept free of TamboUI so it can be unit tested
class FileBrowser(
    preview: ProjectPreview,
) {
    val files: List<ProjectFile> = preview.files

    private var index = preview.buildFileIndex

    // Tracked by path rather than index so the highlight survives re-filtering
    private var highlightedPath: String? = null

    val file: ProjectFile get() = files[index]

    val position: Int get() = index + 1

    // lines() rather than split("\n") so CRLF content leaves no '\r' behind
    var lines: List<String> = file.content.lines()
        private set

    var top: Int = 0
        private set

    var listOpen: Boolean = false
        private set

    var matches: List<ProjectFile> = files
        private set

    var filter: String = ""
        set(value) {
            field = value
            matches = files.filter { it.path.contains(value, ignoreCase = true) }
            if (matches.none { it.path == highlightedPath }) highlightedPath = matches.firstOrNull()?.path
        }

    val highlighted: ProjectFile? get() = matches.firstOrNull { it.path == highlightedPath }

    // The screen knows how many lines fit, so it passes that in rather than the browser remembering it
    fun scrollBy(
        lines: Int,
        visible: Int,
    ) {
        top = (top + lines).coerceIn(0, maxOf(0, this.lines.size - visible))
    }

    fun previous() = show(index - 1)

    fun next() = show(index + 1)

    fun openList() {
        filter = ""
        highlightedPath = file.path
        listOpen = true
    }

    fun closeList() {
        listOpen = false
    }

    fun moveHighlight(by: Int) {
        val at = matches.indexOfFirst { it.path == highlightedPath }
        if (at < 0) return
        highlightedPath = matches[(at + by).coerceIn(matches.indices)].path
    }

    fun openHighlighted() {
        val target = highlighted ?: return
        show(files.indexOf(target))
        listOpen = false
    }

    private fun show(target: Int) {
        index = target.coerceIn(files.indices)
        lines = file.content.lines()
        top = 0
    }
}

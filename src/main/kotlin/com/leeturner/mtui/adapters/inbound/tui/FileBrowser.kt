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

    var lines: List<String> = file.displayLines()
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

    // Long lines wrap onto extra rows rather than being cut off, so copying with the mouse gets the whole line
    fun rows(
        width: Int,
        height: Int,
    ): List<String> =
        lines
            .drop(top)
            .asSequence()
            .flatMap { it.chunked(width.coerceAtLeast(1)).ifEmpty { listOf("") } }
            .take(height)
            .toList()

    // How many lines from the end fit in height rows once wrapped; passed to scrollBy so the last line can be reached
    fun linesAtEnd(
        width: Int,
        height: Int,
    ): Int {
        var rows = 0
        val fitting =
            lines.asReversed().takeWhile { line ->
                rows += (line.length - 1) / width.coerceAtLeast(1) + 1
                rows <= height
            }
        return fitting.size.coerceAtLeast(1)
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
        val clamped = target.coerceIn(files.indices)
        // The ends stop rather than wrap, so staying on the same file must not reset its scroll position
        if (clamped == index) return
        index = clamped
        lines = file.displayLines()
        top = 0
    }
}

// lines() rather than split("\n") so CRLF content leaves no '\r' behind; a tab drawn as-is would garble the screen
// ponytail: a tab is always 4 spaces, not the next tab stop; fine for the indentation Launch generates
private fun ProjectFile.displayLines(): List<String> = content.lines().map { it.replace("\t", "    ") }

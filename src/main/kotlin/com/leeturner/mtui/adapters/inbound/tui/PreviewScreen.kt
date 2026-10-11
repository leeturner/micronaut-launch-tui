package com.leeturner.mtui.adapters.inbound.tui

import com.leeturner.mtui.domain.core.model.ProjectOptions
import dev.tamboui.buffer.Buffer
import dev.tamboui.layout.Rect
import dev.tamboui.style.Style
import dev.tamboui.toolkit.Toolkit.column
import dev.tamboui.toolkit.Toolkit.dialog
import dev.tamboui.toolkit.Toolkit.handleTextInputKey
import dev.tamboui.toolkit.Toolkit.list
import dev.tamboui.toolkit.Toolkit.stack
import dev.tamboui.toolkit.Toolkit.text
import dev.tamboui.toolkit.Toolkit.textInput
import dev.tamboui.toolkit.Toolkit.widget
import dev.tamboui.toolkit.element.Element
import dev.tamboui.toolkit.element.StyledElement
import dev.tamboui.tui.event.KeyEvent
import dev.tamboui.widget.Widget
import dev.tamboui.widgets.input.TextInputState

enum class PreviewAction { NONE, BACK, QUIT }

// The generated files, one at a time, with no borders so selecting with the mouse copies clean lines
class PreviewScreen(
    private val browser: FileBrowser,
) {
    private val filterInput = TextInputState()

    // From the last render, which always comes before the keys that use them
    private var height = 1
    private var width = DIALOG_MIN_WIDTH
    private var linesAtEnd = 1

    fun render(options: ProjectOptions): Element {
        val header = "${browser.file.path}  (${browser.position}/${browser.files.size})  ${options.summary()}"
        val fileView = column(text(header).bold().length(1), widget(FileLines()).fill())
        return if (browser.listOpen) {
            column(stack(fileView, fileList()).fill(), text(FILE_LIST_HINTS).dim().length(1)).fill()
        } else {
            column(fileView.fill(), text(PREVIEW_HINTS).dim().length(1)).fill()
        }
    }

    val help: Help get() = if (browser.listOpen) FILE_LIST_HELP else PREVIEW_HELP

    fun handleKey(event: KeyEvent): PreviewAction =
        when {
            event.isCtrlC -> {
                PreviewAction.QUIT
            }

            browser.listOpen -> {
                handleListKey(event)
                PreviewAction.NONE
            }

            event.isCancel -> {
                PreviewAction.BACK
            }

            else -> {
                handleFileKey(event)
                PreviewAction.NONE
            }
        }

    private fun handleFileKey(event: KeyEvent) {
        when {
            event.isDown || event.isChar('j') -> browser.scrollBy(1, linesAtEnd)
            event.isUp || event.isChar('k') -> browser.scrollBy(-1, linesAtEnd)
            event.isPageDown -> browser.scrollBy(height, linesAtEnd)
            event.isPageUp -> browser.scrollBy(-height, linesAtEnd)
            event.isChar('[') -> browser.previous()
            event.isChar(']') -> browser.next()
            event.isChar('f') || event.isChar('/') -> openList()
        }
    }

    private fun openList() {
        filterInput.clear()
        browser.openList()
    }

    // The filter is fed here rather than focused, because a focused text input would take ↑/↓ for its cursor
    private fun handleListKey(event: KeyEvent) {
        when {
            event.isDown -> browser.moveHighlight(1)
            event.isUp -> browser.moveHighlight(-1)
            event.isConfirm -> browser.openHighlighted()
            event.isCancel -> browser.closeList()
            handleTextInputKey(filterInput, event) -> browser.filter = filterInput.text()
        }
    }

    private fun fileList(): Element {
        val matches = browser.matches
        // Sized to the longest match so file names stay visible, up to the width the file view had last render
        val dialogWidth =
            (matches.maxOfOrNull { it.path.length + DIALOG_CHROME } ?: 0).coerceIn(
                DIALOG_MIN_WIDTH,
                maxOf(width, DIALOG_MIN_WIDTH),
            )
        val rows: List<StyledElement<*>> =
            matches.map {
                val label = text(shortenPath(it.path, dialogWidth - DIALOG_CHROME))
                if (it == browser.highlighted) label.reversed() else label
            }
        return dialog(
            "Files",
            textInput(filterInput).focusable(false).cursorRequiresFocus(false).length(1),
            list(*rows.ifEmpty { listOf(text("No files match").dim()) }.toTypedArray())
                .displayOnly()
                .autoScroll()
                .selected(matches.indexOf(browser.highlighted).coerceAtLeast(0))
                .fill(),
        ).rounded().width(dialogWidth)
    }

    // Drawn straight into its area, which is the only place the height left for the file is known
    private inner class FileLines : Widget {
        override fun render(
            area: Rect,
            buffer: Buffer,
        ) {
            height = area.height().coerceAtLeast(1)
            width = area.width()
            linesAtEnd = browser.linesAtEnd(area.width(), height)
            // Re-clamps the scroll position if the terminal changed size
            browser.scrollBy(0, linesAtEnd)
            browser.rows(area.width(), area.height()).forEachIndexed { i, row ->
                buffer.setString(area.x(), area.y() + i, row, Style.EMPTY)
            }
        }
    }

    private companion object {
        const val DIALOG_MIN_WIDTH = 40

        // Borders plus a column of slack either side of the longest path
        const val DIALOG_CHROME = 4
        const val PREVIEW_HINTS = "j/k scroll · f files · [ ] prev/next · Esc back · ? help"
        const val FILE_LIST_HINTS = "type to filter · ↑/↓ move · Enter open · Esc close · ? help"

        val PREVIEW_HELP =
            Help(
                "Preview keys",
                listOf(
                    KeyHelp("j / ↓", "scroll down"),
                    KeyHelp("k / ↑", "scroll up"),
                    KeyHelp("PgDn / PgUp", "scroll a page"),
                    KeyHelp("[ / ]", "previous or next file"),
                    KeyHelp("f / /", "open the file list"),
                    KeyHelp("Esc", "go back"),
                    KeyHelp("Ctrl+C", "quit"),
                ),
            )

        val FILE_LIST_HELP =
            Help(
                "File list keys",
                listOf(
                    KeyHelp("type", "filter the files"),
                    KeyHelp("↑ / ↓", "move"),
                    KeyHelp("Enter", "open the file"),
                    KeyHelp("Esc", "close the list"),
                    KeyHelp("Ctrl+C", "quit"),
                ),
            )
    }
}

// Cut from the left, because the end of a path is the part that says which file it is
internal fun shortenPath(
    path: String,
    max: Int,
): String = if (path.length <= max) path else "…" + path.takeLast((max - 1).coerceAtLeast(0))

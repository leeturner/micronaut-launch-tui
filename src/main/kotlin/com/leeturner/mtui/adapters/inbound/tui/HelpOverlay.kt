package com.leeturner.mtui.adapters.inbound.tui

import dev.tamboui.toolkit.Toolkit.column
import dev.tamboui.toolkit.Toolkit.dialog
import dev.tamboui.toolkit.Toolkit.row
import dev.tamboui.toolkit.Toolkit.stack
import dev.tamboui.toolkit.Toolkit.text
import dev.tamboui.toolkit.element.Element
import dev.tamboui.tui.event.KeyEvent

data class KeyHelp(
    val keys: String,
    val does: String,
)

data class Help(
    val title: String,
    val keys: List<KeyHelp>,
)

enum class HelpResult { PASS, HANDLED, QUIT }

// The ? popup; it answers every key first so nothing changes behind it while it is open
class HelpOverlay {
    var showing: Help? = null
        private set

    fun open(help: Help) {
        showing = help
    }

    // available is the help the current screen offers, null where ? does nothing
    fun handleKey(
        event: KeyEvent,
        available: Help?,
    ): HelpResult =
        when {
            showing == null && event.isChar('?') && available != null -> {
                showing = available
                HelpResult.HANDLED
            }

            showing == null -> {
                HelpResult.PASS
            }

            event.isCtrlC -> {
                HelpResult.QUIT
            }

            else -> {
                if (event.isChar('?') || event.isCancel) showing = null
                HelpResult.HANDLED
            }
        }

    fun render(behind: Element): Element {
        val help = showing ?: return behind
        val keysWidth = help.keys.maxOf { it.keys.length } + KEYS_GAP
        val rows =
            help.keys.map { row(text(it.keys).bold().length(keysWidth), text(it.does)).length(1) } +
                listOf(text("").length(1), text(CLOSE_HINT).dim().length(1))
        val width = maxOf(keysWidth + help.keys.maxOf { it.does.length }, CLOSE_HINT.length) + DIALOG_CHROME
        return stack(column(behind).dim(), dialog(help.title, *rows.toTypedArray()).rounded().width(width))
    }

    private companion object {
        const val CLOSE_HINT = "? or Esc to close"
        const val KEYS_GAP = 2

        // Borders plus a column of padding either side
        const val DIALOG_CHROME = 4
    }
}

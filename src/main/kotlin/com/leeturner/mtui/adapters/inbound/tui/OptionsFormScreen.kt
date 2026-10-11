package com.leeturner.mtui.adapters.inbound.tui

import com.leeturner.mtui.domain.core.model.MicronautVersion
import com.leeturner.mtui.domain.core.model.SelectOptions
import dev.tamboui.toolkit.Toolkit.column
import dev.tamboui.toolkit.Toolkit.row
import dev.tamboui.toolkit.Toolkit.spinner
import dev.tamboui.toolkit.Toolkit.text
import dev.tamboui.toolkit.Toolkit.textInput
import dev.tamboui.toolkit.element.Element
import dev.tamboui.toolkit.element.StyledElement
import dev.tamboui.toolkit.focus.FocusManager
import dev.tamboui.tui.event.KeyEvent
import dev.tamboui.widgets.input.TextInputState

sealed interface FormAction {
    data object None : FormAction

    data object Submit : FormAction

    data object Quit : FormAction

    data object Preview : FormAction

    data class SwitchVersion(
        val version: MicronautVersion,
    ) : FormAction
}

// The name screen: the name box and the option rows; kept for the whole run so going back keeps what was entered
class OptionsFormScreen(
    options: SelectOptions,
    versions: List<MicronautVersion>,
) {
    val form = OptionsForm(options, versions)
    val nameInput = TextInputState()

    // The row `p` was pressed on, focused again on the way back from the preview
    private var previewedFrom = OptionRow.entries.first()

    // While switching version the rows are dimmed, because they still show the previous server's options
    fun render(
        error: String?,
        fetching: Boolean,
        previewing: Boolean,
        switchingTo: MicronautVersion?,
        focus: FocusManager,
        onSubmit: () -> Unit,
    ): Element =
        column(
            text("Create a Micronaut project").bold(),
            text(""),
            textInput(nameInput)
                .id(NAME_INPUT_ID)
                .title("Name")
                .placeholder("com.example.my-app")
                .rounded()
                .onSubmit(onSubmit),
            when {
                fetching -> row(spinner(), text(" Fetching features…"))
                previewing -> row(spinner(), text(" Fetching preview…"))
                switchingTo != null -> row(spinner(), text(" Loading ${switchingTo.label} options…"))
                else -> text(error ?: "").bold()
            },
            text(""),
            *OptionRow.entries
                .map { optionRow(it, focus.isFocused(it.focusId), dimmed = switchingTo != null) }
                .toTypedArray(),
            text(""),
            text("Tab/j/k move · ←/→ h/l change · p preview · Enter choose features · Esc quit · ? help").dim(),
        )

    fun focusName(focus: FocusManager) = focus.setFocus(NAME_INPUT_ID)

    fun focusPreviewedRow(focus: FocusManager) = focus.setFocus(previewedFrom.focusId)

    // Only keys the name box didn't consume arrive here, so letters typed into it are never treated as commands
    fun handleKey(
        event: KeyEvent,
        focus: FocusManager,
    ): FormAction =
        when {
            event.isCtrlC || event.isCancel -> {
                FormAction.Quit
            }

            event.isConfirm -> {
                FormAction.Submit
            }

            else -> {
                val row = OptionRow.entries.firstOrNull { focus.isFocused(it.focusId) }
                when {
                    row != null && event.isChar('p') -> {
                        previewedFrom = row
                        FormAction.Preview
                    }

                    row != null -> {
                        handleRowKey(event, row, focus)
                    }

                    event.isDown -> {
                        focus.setFocus(OptionRow.entries.first().focusId)
                        FormAction.None
                    }

                    else -> {
                        FormAction.None
                    }
                }
            }
        }

    private fun handleRowKey(
        event: KeyEvent,
        row: OptionRow,
        focus: FocusManager,
    ): FormAction {
        val by = event.step()
        // The other server's options have to load before the version can change
        if (by != 0 && row == OptionRow.VERSION) {
            return form.neighbourVersion(by)?.let(FormAction::SwitchVersion) ?: FormAction.None
        }
        when {
            event.isDown || event.isChar('j') -> row.below()?.let { focus.setFocus(it.focusId) }
            event.isUp || event.isChar('k') -> focus.setFocus(row.above()?.focusId ?: NAME_INPUT_ID)
            by < 0 -> form.previous(row)
            by > 0 -> form.next(row)
        }
        return FormAction.None
    }

    private fun optionRow(
        row: OptionRow,
        focused: Boolean,
        dimmed: Boolean,
    ): StyledElement<*> {
        val label = "${if (focused) "›" else " "} ${row.label.padEnd(LABEL_WIDTH)}"
        val choices =
            form.choices(row).map { choice ->
                val text = "${if (choice.chosen) "(•)" else "( )"} ${choice.label}  "
                when {
                    dimmed -> text(text).dim().length(text.length)
                    choice.chosen -> text(text).green().length(text.length)
                    else -> text(text).length(text.length)
                }
            }
        val labelText = if (focused) text(label).bold().length(label.length) else text(label).length(label.length)
        return row(labelText, *choices.toTypedArray()).id(row.focusId).focusable().length(1)
    }

    companion object {
        private const val NAME_INPUT_ID = "name"
        private const val LABEL_WIDTH = 10

        val HELP =
            Help(
                "Name and options keys",
                listOf(
                    KeyHelp("Tab / Shift+Tab", "move between the name and the options"),
                    KeyHelp("j / ↓", "move down the options"),
                    KeyHelp("k / ↑", "move up the options"),
                    KeyHelp("h l / ← →", "change the option"),
                    KeyHelp("p", "preview the generated files"),
                    KeyHelp("Enter", "choose features"),
                    KeyHelp("Esc", "quit"),
                    KeyHelp("Ctrl+C", "quit"),
                ),
            )
    }
}

// The name box takes every printable key before the app sees it, so a ? typed there is taken back out afterwards.
// A name can't contain ?, so nothing is lost. Only the character before the cursor is checked,
// so a pasted name keeps its ? for the name check to reject
internal fun TextInputState.takeTypedQuestionMark(): Boolean {
    if (text().getOrNull(cursorPosition() - 1) != '?') return false
    deleteBackward()
    return true
}

private val OptionRow.focusId: String get() = "option-${name.lowercase()}"

// -1 / 1 for a key that moves the mark left / right, 0 otherwise
private fun KeyEvent.step(): Int =
    when {
        isLeft || isChar('h') -> -1
        isRight || isChar('l') -> 1
        else -> 0
    }

private fun OptionRow.below(): OptionRow? = OptionRow.entries.getOrNull(ordinal + 1)

private fun OptionRow.above(): OptionRow? = OptionRow.entries.getOrNull(ordinal - 1)

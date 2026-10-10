package com.leeturner.mtui.adapters.inbound.tui

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

enum class FormAction { NONE, SUBMIT, QUIT }

// The name screen: the name box and the option rows; kept for the whole run so going back keeps what was entered
class OptionsFormScreen(
    options: SelectOptions,
) {
    val form = OptionsForm(options)
    val nameInput = TextInputState()

    fun render(
        error: String?,
        fetching: Boolean,
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
            if (fetching) row(spinner(), text(" Fetching features…")) else text(error ?: "").bold(),
            text(""),
            *OptionRow.entries.map { optionRow(it, focus.isFocused(it.focusId)) }.toTypedArray(),
            text(""),
            text("Tab/j/k move · ←/→ h/l change · Enter choose features · Esc quit").dim(),
        )

    fun focusName(focus: FocusManager) = focus.setFocus(NAME_INPUT_ID)

    // Only keys the name box didn't consume arrive here, so letters typed into it are never treated as commands
    fun handleKey(
        event: KeyEvent,
        focus: FocusManager,
    ): FormAction =
        when {
            event.isCtrlC || event.isCancel -> {
                FormAction.QUIT
            }

            event.isConfirm -> {
                FormAction.SUBMIT
            }

            else -> {
                val row = OptionRow.entries.firstOrNull { focus.isFocused(it.focusId) }
                when {
                    row != null -> handleRowKey(event, row, focus)
                    event.isDown -> focus.setFocus(OptionRow.TYPE.focusId)
                }
                FormAction.NONE
            }
        }

    private fun handleRowKey(
        event: KeyEvent,
        row: OptionRow,
        focus: FocusManager,
    ) {
        when {
            event.isDown || event.isChar('j') -> row.below()?.let { focus.setFocus(it.focusId) }
            event.isUp || event.isChar('k') -> focus.setFocus(row.above()?.focusId ?: NAME_INPUT_ID)
            event.isLeft || event.isChar('h') -> form.previous(row)
            event.isRight || event.isChar('l') -> form.next(row)
        }
    }

    private fun optionRow(
        row: OptionRow,
        focused: Boolean,
    ): StyledElement<*> {
        val label = "${if (focused) "›" else " "} ${row.label.padEnd(LABEL_WIDTH)}"
        val choices =
            form.choices(row).map { choice ->
                val text = "${if (choice.chosen) "(•)" else "( )"} ${choice.label}  "
                if (choice.chosen) text(text).green().length(text.length) else text(text).length(text.length)
            }
        val labelText = if (focused) text(label).bold().length(label.length) else text(label).length(label.length)
        return row(labelText, *choices.toTypedArray()).id(row.focusId).focusable().length(1)
    }

    private companion object {
        const val NAME_INPUT_ID = "name"
        const val LABEL_WIDTH = 10
    }
}

private val OptionRow.focusId: String get() = "option-${name.lowercase()}"

private fun OptionRow.below(): OptionRow? = OptionRow.entries.getOrNull(ordinal + 1)

private fun OptionRow.above(): OptionRow? = OptionRow.entries.getOrNull(ordinal - 1)

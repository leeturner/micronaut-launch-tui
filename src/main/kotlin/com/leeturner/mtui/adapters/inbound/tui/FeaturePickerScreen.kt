package com.leeturner.mtui.adapters.inbound.tui

import com.leeturner.mtui.domain.core.model.Feature
import com.leeturner.mtui.domain.core.model.ProjectName
import com.leeturner.mtui.domain.core.model.ProjectOptions
import dev.tamboui.toolkit.Toolkit.column
import dev.tamboui.toolkit.Toolkit.list
import dev.tamboui.toolkit.Toolkit.panel
import dev.tamboui.toolkit.Toolkit.row
import dev.tamboui.toolkit.Toolkit.spinner
import dev.tamboui.toolkit.Toolkit.text
import dev.tamboui.toolkit.Toolkit.textInput
import dev.tamboui.toolkit.element.Element
import dev.tamboui.toolkit.element.StyledElement
import dev.tamboui.toolkit.focus.FocusManager
import dev.tamboui.tui.event.KeyEvent
import dev.tamboui.widgets.input.TextInputState

enum class PickerAction { NONE, GENERATE, BACK, QUIT }

// The feature picker screen; kept for the whole run so the selection survives going back to the name
class FeaturePickerScreen(
    features: List<Feature>,
    private val listFocusId: String,
    selected: List<String> = emptyList(),
) {
    val picker = FeaturePicker(features, selected)
    private val searchInput = TextInputState()

    fun render(
        options: ProjectOptions,
        name: ProjectName,
        status: StyledElement<*>,
        focus: FocusManager,
    ): Element {
        // Typing in the search box filters live, so the query is picked up on every render
        if (picker.query != searchInput.text()) picker.query = searchInput.text()
        val (rows, highlightedRow) = featureRows()
        val selected = picker.selected
        return column(
            row(
                text("Create a Micronaut project").bold(),
                text("   Name: ${name.value}   "),
                text(options.summary()).dim(),
            ).length(1),
            textInput(searchInput)
                .id(SEARCH_INPUT_ID)
                .title("Search")
                .placeholder("Press / to search")
                .rounded()
                .onSubmit { focus.setFocus(listFocusId) },
            row(
                list(*rows.toTypedArray())
                    .title("Features")
                    .rounded()
                    .displayOnly()
                    .autoScroll()
                    .selected(highlightedRow)
                    .fill(),
                panel("Selected (${selected.size})", column(*selected.map { text(it).green() }.toTypedArray()))
                    .rounded()
                    .length(SELECTED_PANE_WIDTH),
            ).fill(),
            text(picker.highlighted?.description ?: "").dim().length(1),
            status.length(1),
            text("j/k move · Space toggle · / search · x clear · Enter generate · Esc back").dim().length(1),
        )
    }

    fun handleKey(
        event: KeyEvent,
        focus: FocusManager,
    ): PickerAction =
        when {
            focus.isFocused(SEARCH_INPUT_ID) -> handleSearchKey(event, focus)
            else -> handleListKey(event, focus)
        }

    // Only keys the search box didn't consume arrive here, so letters are always typed, never treated as commands
    private fun handleSearchKey(
        event: KeyEvent,
        focus: FocusManager,
    ): PickerAction {
        if (event.isCtrlC) return PickerAction.QUIT
        if (event.isCancel) focus.setFocus(listFocusId)
        return PickerAction.NONE
    }

    private fun handleListKey(
        event: KeyEvent,
        focus: FocusManager,
    ): PickerAction =
        when {
            event.isCtrlC -> {
                PickerAction.QUIT
            }

            event.isConfirm -> {
                PickerAction.GENERATE
            }

            event.isCancel && searchInput.text().isEmpty() -> {
                PickerAction.BACK
            }

            else -> {
                when {
                    event.isCancel -> searchInput.clear()
                    event.isDown || event.isChar('j') -> picker.moveDown()
                    event.isUp || event.isChar('k') -> picker.moveUp()
                    event.isChar(' ') -> picker.toggle()
                    event.isChar('x') -> picker.clearSelection()
                    event.isChar('/') -> focus.setFocus(SEARCH_INPUT_ID)
                }
                PickerAction.NONE
            }
        }

    // Category headings and feature rows, plus the row index of the highlighted feature
    private fun featureRows(): Pair<List<StyledElement<*>>, Int> {
        val rows = mutableListOf<StyledElement<*>>()
        var highlightedRow = 0
        picker.groups.forEach { group ->
            rows += text(group.category).bold().yellow()
            group.features.forEach { feature ->
                if (feature == picker.highlighted) highlightedRow = rows.size
                rows += featureRow(feature)
            }
        }
        if (rows.isEmpty()) rows += text("No features match").dim()
        return rows to highlightedRow
    }

    private fun featureRow(feature: Feature): StyledElement<*> {
        val selected = picker.isSelected(feature)
        val tags = listOfNotNull("preview".takeIf { feature.preview }, "community".takeIf { feature.community })
        val label =
            "${if (selected) "[x]" else "[ ]"} ${feature.name.padEnd(NAME_COLUMN_WIDTH)} ${feature.title}" +
                tags.joinToString("") { "  [$it]" }
        val row = if (selected) text(label).green() else text(label)
        // The list draws no highlight of its own in display-only mode, so the highlighted row is marked here
        return if (feature == picker.highlighted) row.reversed() else row
    }

    companion object {
        fun status(
            error: String?,
            generating: Boolean,
        ): StyledElement<*> = if (generating) row(spinner(), text(" Generating…")) else text(error ?: "").bold()

        private const val SEARCH_INPUT_ID = "search"
        private const val NAME_COLUMN_WIDTH = 30
        private const val SELECTED_PANE_WIDTH = 32
    }
}

private fun ProjectOptions.summary(): String =
    listOf(
        type.label,
        language.label,
        build.label,
        test.label,
        jdk.label,
    ).joinToString(" · ")

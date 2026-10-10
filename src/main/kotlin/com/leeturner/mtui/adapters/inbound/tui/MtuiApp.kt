package com.leeturner.mtui.adapters.inbound.tui

import arrow.core.left
import com.leeturner.mtui.domain.core.model.EmptySelectOptionsError
import com.leeturner.mtui.domain.core.model.ProjectName
import com.leeturner.mtui.domain.core.model.SelectOptions
import com.leeturner.mtui.domain.core.model.SelectOptionsError
import com.leeturner.mtui.domain.core.model.UnexpectedSelectOptionRetrievalError
import com.leeturner.mtui.domain.core.ports.SelectOptionRetriever
import com.leeturner.mtui.domain.core.services.ProjectGenerator
import dev.tamboui.toolkit.Toolkit.column
import dev.tamboui.toolkit.Toolkit.row
import dev.tamboui.toolkit.Toolkit.spinner
import dev.tamboui.toolkit.Toolkit.text
import dev.tamboui.toolkit.Toolkit.textInput
import dev.tamboui.toolkit.app.ToolkitApp
import dev.tamboui.toolkit.element.Element
import dev.tamboui.toolkit.event.EventResult
import dev.tamboui.tui.event.KeyEvent
import dev.tamboui.widgets.input.TextInputState
import java.nio.file.Path
import java.util.concurrent.CompletableFuture

sealed interface MtuiOutcome {
    data class Created(
        val path: Path,
    ) : MtuiOutcome

    data object Cancelled : MtuiOutcome

    data class Failed(
        val message: String,
    ) : MtuiOutcome
}

class MtuiApp(
    private val selectOptionRetriever: SelectOptionRetriever,
    private val generator: ProjectGenerator,
    private val workingDir: Path,
) : ToolkitApp() {
    var outcome: MtuiOutcome = MtuiOutcome.Cancelled
        private set

    @Volatile
    private var screen: Screen = Screen.Loading

    private val nameInput = TextInputState()

    override fun onStart() {
        background(
            work = {
                selectOptionRetriever
                    .getSelectOptions()
                    .fold<Screen>({ Screen.LoadFailed(it.text()) }, { Screen.Form(it) })
            },
            onFailure = { Screen.LoadFailed(it.message ?: it.toString()) },
        ) { next ->
            screen = next
            if (next is Screen.Form) runner().focusManager().setFocus(NAME_INPUT_ID)
        }
    }

    override fun render(): Element {
        val content =
            when (val current = screen) {
                Screen.Loading -> row(spinner(), text(" Fetching options from Micronaut Launch…"))
                is Screen.LoadFailed -> loadFailed(current.message)
                is Screen.Form -> form(current.options, current.error, generating = false)
                is Screen.Generating -> form(current.options, error = null, generating = true)
            }
        return column(content).id("root").focusable().onKeyEvent(::handleKey)
    }

    private fun loadFailed(message: String): Element =
        column(
            text("Could not load options from Micronaut Launch").bold(),
            text(message),
            text(""),
            text("Press any key to exit").dim(),
        )

    private fun form(
        options: SelectOptions,
        error: String?,
        generating: Boolean,
    ): Element =
        column(
            text("Create a Micronaut project").bold(),
            text(""),
            textInput(nameInput)
                .id(NAME_INPUT_ID)
                .title("Name")
                .placeholder("com.example.my-app")
                .rounded()
                .onSubmit { submit(options) },
            if (generating) row(spinner(), text(" Generating…")) else text(error ?: "").bold(),
            text(""),
            text("Type:      ${options.defaultType.label}").dim(),
            text("Language:  ${options.defaultLanguage.label}").dim(),
            text("Build:     ${options.defaultBuildType.label}").dim(),
            text("Test:      ${options.defaultTestFramework.label}").dim(),
            text("JDK:       ${options.defaultJdkVersion.label}").dim(),
            text(""),
            text("Enter: generate · Esc: quit").dim(),
        )

    private fun handleKey(event: KeyEvent): EventResult =
        when (val current = screen) {
            is Screen.LoadFailed -> {
                finish(MtuiOutcome.Failed(current.message))
            }

            // Quitting mid-write would leave a half-written project, so keys are ignored until generation finishes
            is Screen.Generating -> {
                EventResult.HANDLED
            }

            else -> {
                if (event.isCancel || event.isCtrlC) finish(MtuiOutcome.Cancelled) else EventResult.UNHANDLED
            }
        }

    private fun submit(options: SelectOptions) {
        if (screen !is Screen.Form) return
        ProjectName.parse(nameInput.text()).fold(
            { screen = Screen.Form(options, it.message) },
            { name ->
                screen = Screen.Generating(options)
                background(
                    work = { generator.generate(options.defaultType, name, emptyList(), workingDir).mapLeft { it.message } },
                    onFailure = { (it.message ?: it.toString()).left() },
                ) { result ->
                    result.fold(
                        { message -> screen = Screen.Form(options, message) },
                        { path -> finish(MtuiOutcome.Created(path)) },
                    )
                }
            },
        )
    }

    private fun finish(result: MtuiOutcome): EventResult {
        outcome = result
        quit()
        return EventResult.HANDLED
    }

    // Runs blocking work off the render thread and applies the result back on it
    private fun <T> background(
        work: () -> T,
        onFailure: (Throwable) -> T,
        apply: (T) -> Unit,
    ) {
        CompletableFuture.runAsync {
            val result = runCatching(work).getOrElse(onFailure)
            runner().runOnRenderThread { apply(result) }
        }
    }

    private sealed interface Screen {
        data object Loading : Screen

        data class LoadFailed(
            val message: String,
        ) : Screen

        data class Form(
            val options: SelectOptions,
            val error: String? = null,
        ) : Screen

        data class Generating(
            val options: SelectOptions,
        ) : Screen
    }

    private companion object {
        const val NAME_INPUT_ID = "name"
    }
}

private fun SelectOptionsError.text(): String =
    when (this) {
        is UnexpectedSelectOptionRetrievalError -> message
        is EmptySelectOptionsError -> message
    }

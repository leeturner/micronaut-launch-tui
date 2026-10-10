package com.leeturner.mtui.adapters.inbound.tui

import arrow.core.left
import com.leeturner.mtui.domain.core.model.ProjectName
import com.leeturner.mtui.domain.core.model.ProjectOptions
import com.leeturner.mtui.domain.core.model.SelectOptions
import com.leeturner.mtui.domain.core.services.CatalogLoader
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
    private val catalogLoader: CatalogLoader,
    private val generator: ProjectGenerator,
    private val workingDir: Path,
) : ToolkitApp() {
    var outcome: MtuiOutcome = MtuiOutcome.Cancelled
        private set

    @Volatile
    private var screen: Screen = Screen.Loading

    private val nameInput = TextInputState()

    // Replaced once features load
    private var pickerScreen = FeaturePickerScreen(emptyList(), ROOT_ID)

    override fun onStart() {
        background(
            work = { catalogLoader.load().mapLeft { it.message } },
            onFailure = { (it.message ?: it.toString()).left() },
        ) { loaded ->
            loaded.fold(
                { screen = Screen.LoadFailed(it) },
                { catalog ->
                    pickerScreen = FeaturePickerScreen(catalog.features, ROOT_ID)
                    showForm(catalog.options)
                },
            )
        }
    }

    override fun render(): Element {
        val focus = runner().focusManager()
        val content =
            when (val current = screen) {
                Screen.Loading -> {
                    row(spinner(), text(" Fetching options from Micronaut Launch…"))
                }

                is Screen.LoadFailed -> {
                    loadFailed(current.message)
                }

                is Screen.Form -> {
                    form(current.options, current.error)
                }

                is Screen.Picker -> {
                    val status = FeaturePickerScreen.status(current.error, generating = false)
                    pickerScreen.render(current.options, current.name, status, focus)
                }

                is Screen.Generating -> {
                    val status = FeaturePickerScreen.status(error = null, generating = true)
                    pickerScreen.render(current.options, current.name, status, focus)
                }
            }
        return column(content).id(ROOT_ID).focusable().onKeyEvent(::handleKey)
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
    ): Element =
        column(
            text("Create a Micronaut project").bold(),
            text(""),
            textInput(nameInput)
                .id(NAME_INPUT_ID)
                .title("Name")
                .placeholder("com.example.my-app")
                .rounded()
                .onSubmit { submitName(options) },
            text(error ?: "").bold(),
            text(""),
            text("Type:      ${options.defaultType.label}").dim(),
            text("Language:  ${options.defaultLanguage.label}").dim(),
            text("Build:     ${options.defaultBuildType.label}").dim(),
            text("Test:      ${options.defaultTestFramework.label}").dim(),
            text("JDK:       ${options.defaultJdkVersion.label}").dim(),
            text(""),
            text("Enter: choose features · Esc: quit").dim(),
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

            is Screen.Picker -> {
                when (pickerScreen.handleKey(event, runner().focusManager())) {
                    PickerAction.GENERATE -> generate(current.options, current.name)
                    PickerAction.BACK -> showForm(current.options)
                    PickerAction.QUIT -> finish(MtuiOutcome.Cancelled)
                    PickerAction.NONE -> Unit
                }
                // Everything is handled so default bindings such as q-to-quit never fire on the picker
                EventResult.HANDLED
            }

            else -> {
                if (event.isCancel || event.isCtrlC) finish(MtuiOutcome.Cancelled) else EventResult.UNHANDLED
            }
        }

    private fun showForm(options: SelectOptions) {
        screen = Screen.Form(options)
        runner().focusManager().setFocus(NAME_INPUT_ID)
    }

    private fun submitName(options: SelectOptions) {
        if (screen !is Screen.Form) return
        ProjectName
            .parse(nameInput.text())
            .mapLeft { it.message }
            .fold(
                { screen = Screen.Form(options, it) },
                { name ->
                    generator.checkAvailable(name, workingDir).fold(
                        { screen = Screen.Form(options, it.message) },
                        {
                            screen = Screen.Picker(options, name)
                            runner().focusManager().setFocus(ROOT_ID)
                        },
                    )
                },
            )
    }

    private fun generate(
        options: SelectOptions,
        name: ProjectName,
    ) {
        val features = pickerScreen.picker.selected
        val chosen = ProjectOptions.defaultsFrom(options)
        screen = Screen.Generating(options, name)
        background(
            work = { generator.generate(chosen, name, features, workingDir).mapLeft { it.message } },
            onFailure = { (it.message ?: it.toString()).left() },
        ) { result ->
            result.fold(
                { message -> screen = Screen.Picker(options, name, message) },
                { path -> finish(MtuiOutcome.Created(path)) },
            )
        }
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

        data class Picker(
            val options: SelectOptions,
            val name: ProjectName,
            val error: String? = null,
        ) : Screen

        data class Generating(
            val options: SelectOptions,
            val name: ProjectName,
        ) : Screen
    }

    private companion object {
        const val ROOT_ID = "root"
        const val NAME_INPUT_ID = "name"
    }
}

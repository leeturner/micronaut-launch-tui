package com.leeturner.mtui.adapters.inbound.tui

import arrow.core.left
import com.leeturner.mtui.domain.core.model.ApplicationType
import com.leeturner.mtui.domain.core.model.Language
import com.leeturner.mtui.domain.core.model.ProjectName
import com.leeturner.mtui.domain.core.model.ProjectOptions
import com.leeturner.mtui.domain.core.services.CatalogLoader
import com.leeturner.mtui.domain.core.services.ProjectGenerator
import dev.tamboui.toolkit.Toolkit.column
import dev.tamboui.toolkit.Toolkit.row
import dev.tamboui.toolkit.Toolkit.spinner
import dev.tamboui.toolkit.Toolkit.text
import dev.tamboui.toolkit.app.ToolkitApp
import dev.tamboui.toolkit.element.Element
import dev.tamboui.toolkit.event.EventResult
import dev.tamboui.tui.event.KeyEvent
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

    // Created once the options load
    private lateinit var formScreen: OptionsFormScreen

    // Replaced whenever features are fetched for a different type and language
    private var pickerScreen = FeaturePickerScreen(emptyList(), ROOT_ID)

    // The type and language the picker's features were fetched for; null until the first fetch
    private var featuresFor: Pair<ApplicationType, Language>? = null

    override fun onStart() {
        background(
            work = { catalogLoader.loadOptions().mapLeft { it.message } },
            onFailure = { (it.message ?: it.toString()).left() },
        ) { loaded ->
            loaded.fold(
                { screen = Screen.LoadFailed(it) },
                { options ->
                    formScreen = OptionsFormScreen(options)
                    showForm()
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
                    formScreen.render(current.error, current.fetching, focus, ::submitName)
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
        // Not a Tab stop on the form, where Tab moves between the name box and the option rows
        return column(content).id(ROOT_ID).focusable(screen !is Screen.Form).onKeyEvent(::handleKey)
    }

    private fun loadFailed(message: String): Element =
        column(
            text("Could not load options from Micronaut Launch").bold(),
            text(message),
            text(""),
            text("Press any key to exit").dim(),
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
                    PickerAction.BACK -> showForm()
                    PickerAction.QUIT -> finish(MtuiOutcome.Cancelled)
                    PickerAction.NONE -> Unit
                }
                // Everything is handled so default bindings such as q-to-quit never fire on the picker
                EventResult.HANDLED
            }

            is Screen.Form -> {
                handleFormKey(event, current)
            }

            Screen.Loading -> {
                if (event.isCancel || event.isCtrlC) finish(MtuiOutcome.Cancelled) else EventResult.UNHANDLED
            }
        }

    // Keys are ignored while features are fetched, so nothing can start a second fetch
    private fun handleFormKey(
        event: KeyEvent,
        form: Screen.Form,
    ): EventResult {
        if (!form.fetching) {
            when (formScreen.handleKey(event, runner().focusManager())) {
                FormAction.SUBMIT -> submitName()
                FormAction.QUIT -> finish(MtuiOutcome.Cancelled)
                FormAction.NONE -> Unit
            }
        }
        return EventResult.HANDLED
    }

    // Errors are about the name, so the name box is focused to fix them
    private fun showForm(error: String? = null) {
        screen = Screen.Form(error)
        formScreen.focusName(runner().focusManager())
    }

    // Enter can arrive both from the name box and as an unconsumed key, so anything but an idle form is ignored
    private fun submitName() {
        if ((screen as? Screen.Form)?.fetching != false) return
        ProjectName
            .parse(formScreen.nameInput.text())
            .mapLeft { it.message }
            .fold(
                { showForm(it) },
                { name ->
                    generator.checkAvailable(name, workingDir).fold(
                        { showForm(it.message) },
                        { openPicker(name) },
                    )
                },
            )
    }

    // Features depend only on type and language, so the picker is kept as it was left unless one of those changed
    private fun openPicker(name: ProjectName) {
        val chosen = formScreen.form.chosen
        val wanted = chosen.type to chosen.language
        if (wanted == featuresFor) {
            screen = Screen.Picker(chosen, name)
            runner().focusManager().setFocus(ROOT_ID)
            return
        }
        screen = Screen.Form(fetching = true)
        // Moved off the name box so typing is ignored along with every other key while fetching
        runner().focusManager().setFocus(ROOT_ID)
        background(
            work = { catalogLoader.loadFeatures(chosen.type, chosen.language).mapLeft { it.message } },
            onFailure = { (it.message ?: it.toString()).left() },
        ) { loaded ->
            loaded.fold(
                { message -> showForm(message) },
                { features ->
                    pickerScreen = FeaturePickerScreen(features, ROOT_ID, pickerScreen.picker.selected)
                    featuresFor = wanted
                    openPicker(name)
                },
            )
        }
    }

    private fun generate(
        options: ProjectOptions,
        name: ProjectName,
    ) {
        val features = pickerScreen.picker.selected
        screen = Screen.Generating(options, name)
        background(
            work = { generator.generate(options, name, features, workingDir).mapLeft { it.message } },
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
            val error: String? = null,
            val fetching: Boolean = false,
        ) : Screen

        data class Picker(
            val options: ProjectOptions,
            val name: ProjectName,
            val error: String? = null,
        ) : Screen

        data class Generating(
            val options: ProjectOptions,
            val name: ProjectName,
        ) : Screen
    }

    private companion object {
        const val ROOT_ID = "root"
    }
}

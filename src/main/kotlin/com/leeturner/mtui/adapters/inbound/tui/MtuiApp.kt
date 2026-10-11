package com.leeturner.mtui.adapters.inbound.tui

import arrow.core.left
import com.leeturner.mtui.domain.core.model.MicronautVersion
import com.leeturner.mtui.domain.core.model.ProjectName
import com.leeturner.mtui.domain.core.model.ProjectOptions
import com.leeturner.mtui.domain.core.model.hasSameFeaturesAs
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
import java.time.Duration
import java.util.concurrent.CompletableFuture

private fun loadFailed(message: String): Element =
    column(
        text("Could not load options from Micronaut Launch").bold(),
        text(message),
        text(""),
        text("Press any key to exit").dim(),
    )

sealed interface MtuiOutcome {
    data class Created(
        val path: Path,
    ) : MtuiOutcome

    data object Cancelled : MtuiOutcome

    data class Failed(
        val message: String,
    ) : MtuiOutcome
}

// One function per step of each flow; they share the screen state, so splitting the class would scatter it
@Suppress("TooManyFunctions")
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

    // The options the picker's features were fetched for; null until the first fetch
    private var featuresFor: ProjectOptions? = null

    override fun onStart() {
        background(
            work = { catalogLoader.loadStart(VERSIONS_TIMEOUT).mapLeft { it.message } },
            onFailure = { (it.message ?: it.toString()).left() },
        ) { loaded ->
            loaded.fold(
                { screen = Screen.LoadFailed(it) },
                { (options, versions) ->
                    formScreen = OptionsFormScreen(options, versions)
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
                    formScreen.render(
                        current.error,
                        current.fetching,
                        current.previewing,
                        current.switchingTo,
                        focus,
                        ::submitName,
                    )
                }

                is Screen.Picker -> {
                    val loading = "Fetching preview…".takeIf { current.previewing }
                    val status = FeaturePickerScreen.status(current.error, loading)
                    pickerScreen.render(current.options, current.name, status, focus)
                }

                is Screen.Generating -> {
                    val status = FeaturePickerScreen.status(error = null, loading = "Generating…")
                    pickerScreen.render(current.options, current.name, status, focus)
                }

                is Screen.Preview -> {
                    current.screen.render(current.options)
                }
            }
        // Not a Tab stop on the form, where Tab moves between the name box and the option rows
        return column(content).id(ROOT_ID).focusable(screen !is Screen.Form).onKeyEvent(::handleKey)
    }

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
                handlePickerKey(event, current)
            }

            is Screen.Form -> {
                handleFormKey(event, current)
            }

            is Screen.Preview -> {
                when (current.screen.handleKey(event)) {
                    PreviewAction.BACK -> back(current.back)
                    PreviewAction.QUIT -> finish(MtuiOutcome.Cancelled)
                    PreviewAction.NONE -> Unit
                }
                EventResult.HANDLED
            }

            Screen.Loading -> {
                if (event.isCancel || event.isCtrlC) finish(MtuiOutcome.Cancelled) else EventResult.UNHANDLED
            }
        }

    // Keys other than Ctrl+C are ignored while the preview loads
    private fun handlePickerKey(
        event: KeyEvent,
        picker: Screen.Picker,
    ): EventResult {
        if (picker.previewing) {
            if (event.isCtrlC) finish(MtuiOutcome.Cancelled)
        } else {
            when (pickerScreen.handleKey(event, runner().focusManager())) {
                PickerAction.GENERATE -> generate(picker.options, picker.name)
                PickerAction.PREVIEW -> previewFromPicker(picker.options, picker.name)
                PickerAction.BACK -> showForm()
                PickerAction.QUIT -> finish(MtuiOutcome.Cancelled)
                PickerAction.NONE -> Unit
            }
        }
        // Everything is handled so default bindings such as q-to-quit never fire on the picker
        return EventResult.HANDLED
    }

    // Keys other than Ctrl+C are ignored while features, options or the preview load, so nothing starts a second load
    private fun handleFormKey(
        event: KeyEvent,
        form: Screen.Form,
    ): EventResult {
        if (event.isCtrlC) return finish(MtuiOutcome.Cancelled)
        if (!form.busy) {
            when (val action = formScreen.handleKey(event, runner().focusManager())) {
                FormAction.Submit -> submitName()
                FormAction.Quit -> finish(MtuiOutcome.Cancelled)
                FormAction.Preview -> previewFromForm()
                is FormAction.SwitchVersion -> switchVersion(action.version)
                FormAction.None -> Unit
            }
        }
        return EventResult.HANDLED
    }

    // Focus stays on the version row, and a failure leaves the form as it was
    private fun switchVersion(version: MicronautVersion) {
        screen = Screen.Form(switchingTo = version)
        background(
            work = { catalogLoader.switchServer(formScreen.form.chosen, version).mapLeft { it.message } },
            onFailure = { (it.message ?: it.toString()).left() },
        ) { loaded ->
            loaded.fold(
                { message -> screen = Screen.Form(error = message) },
                {
                    formScreen.form.switchTo(it)
                    screen = Screen.Form()
                },
            )
        }
    }

    // Errors are about the name, so the name box is focused to fix them
    private fun showForm(error: String? = null) {
        screen = Screen.Form(error)
        formScreen.focusName(runner().focusManager())
    }

    // Enter can arrive both from the name box and as an unconsumed key, so anything but an idle form is ignored
    private fun submitName() {
        if ((screen as? Screen.Form)?.busy != false) return
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

    // Features depend only on server, type and language, so the picker is kept as it was left unless one changed
    private fun openPicker(name: ProjectName) {
        val chosen = formScreen.form.chosen
        if (chosen.hasSameFeaturesAs(featuresFor)) {
            screen = Screen.Picker(chosen, name)
            runner().focusManager().setFocus(ROOT_ID)
            return
        }
        screen = Screen.Form(fetching = true)
        // Moved off the name box so typing is ignored along with every other key while fetching
        runner().focusManager().setFocus(ROOT_ID)
        background(
            work = {
                catalogLoader.loadFeatures(chosen.version.server, chosen.type, chosen.language).mapLeft { it.message }
            },
            onFailure = { (it.message ?: it.toString()).left() },
        ) { loaded ->
            loaded.fold(
                { message -> showForm(message) },
                { features ->
                    pickerScreen = FeaturePickerScreen(features, ROOT_ID, pickerScreen.picker.selected)
                    featuresFor = chosen
                    openPicker(name)
                },
            )
        }
    }

    // Errors from Launch leave the focus on the option row, so pressing p again retries rather than typing a "p"
    private fun previewFromForm() {
        ProjectName
            .parse(formScreen.nameInput.text())
            .mapLeft { it.message }
            .fold(
                { showForm(it) },
                { name ->
                    val chosen = formScreen.form.chosen
                    screen = Screen.Form(previewing = true)
                    preview(chosen, name, emptyList(), Screen.Form()) { Screen.Form(error = it) }
                },
            )
    }

    private fun previewFromPicker(
        options: ProjectOptions,
        name: ProjectName,
    ) {
        screen = Screen.Picker(options, name, previewing = true)
        val back = Screen.Picker(options, name)
        preview(options, name, pickerScreen.picker.selected, back) { back.copy(error = it) }
    }

    // back is the screen to restore on Esc; failed is the screen that shows Launch's message
    private fun preview(
        options: ProjectOptions,
        name: ProjectName,
        features: List<String>,
        back: Screen,
        failed: (String) -> Screen,
    ) {
        background(
            work = { generator.preview(options, name, features).mapLeft { it.message } },
            onFailure = { (it.message ?: it.toString()).left() },
        ) { result ->
            result.fold(
                { message -> screen = failed(message) },
                {
                    screen = Screen.Preview(options, name, back, PreviewScreen(FileBrowser(it)))
                    runner().focusManager().setFocus(ROOT_ID)
                },
            )
        }
    }

    private fun back(to: Screen) {
        screen = to
        val focus = runner().focusManager()
        if (to is Screen.Form) formScreen.focusPreviewedRow(focus) else focus.setFocus(ROOT_ID)
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
            val switchingTo: MicronautVersion? = null,
            val previewing: Boolean = false,
        ) : Screen {
            val busy get() = fetching || switchingTo != null || previewing
        }

        data class Picker(
            val options: ProjectOptions,
            val name: ProjectName,
            val error: String? = null,
            val previewing: Boolean = false,
        ) : Screen

        // back is the form or picker that Esc restores, as it was left
        data class Preview(
            val options: ProjectOptions,
            val name: ProjectName,
            val back: Screen,
            val screen: PreviewScreen,
        ) : Screen

        data class Generating(
            val options: ProjectOptions,
            val name: ProjectName,
        ) : Screen
    }

    private companion object {
        const val ROOT_ID = "root"
        val VERSIONS_TIMEOUT: Duration = Duration.ofSeconds(2)
    }
}

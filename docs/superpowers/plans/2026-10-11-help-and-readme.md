# Help and README Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** `?` opens a popup listing the current screen's keys, and the README is rewritten with screenshots made by committed vhs tapes.

**Architecture:** A small `HelpOverlay` class in the TUI adapter holds which help is open, answers keys first, and draws the popup over the screen. Each screen keeps its help list next to its hint line. `MtuiApp` asks the overlay first with every key and wraps whatever it renders.

**Tech Stack:** Kotlin, TamboUI 0.5.0 toolkit, JUnit 5 + Strikt, vhs 0.12.

**Spec:** `docs/superpowers/specs/2026-10-11-help-and-readme-design.md`

## Global Constraints

- Gradle must run on Java 25 (`JAVA_HOME` set to the sdkman `25-tem` install; Java 26 breaks kapt). `./gradlew build` runs tests, Kotlinter and Detekt and must pass.
- Commit with `git commit --no-gpg-sign`, ending each message with `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>`.
- Everything stays in `com.leeturner.mtui.adapters.inbound.tui`: no new port or adapter (ArchUnit checks this).
- `?` opens help on the form (also from the name box), the picker list, the preview and the preview's file list. In the picker's search box `?` is typed.
- While help is open: `?` or `Esc` closes it, `Ctrl+C` quits, every other key is ignored.
- Every hint line ends with `· ? help`.
- No help, and `?` does nothing, on loading, load failed, generating, or while the form or picker is busy (fetching, switching version, previewing).
- Comment density and naming follow the existing TUI files: short `//` comments that say why.

## Review Focus

- `?` pressed in the middle of the name (cursor not at the end): help opens and the `?` is removed from where it was typed, the rest of the name and the cursor unchanged. Test in Task 2.
- `?` pasted as part of a longer name (e.g. `demo?x`): the existing name validation rejects it; help must not open or eat other characters. Test in Task 2.
- `Esc` while help is open on the form must close help, not quit the app (the form's own `Esc` quits). Test in Task 1 (overlay answers first).
- Closing help on the preview's file list must leave the list open with its filter text intact. Test in Task 2.
- A load finishing behind the popup cannot happen, because every key that starts one is ignored while help is open; no test, but the reviewer should check no `background(...)` path is reachable while `HelpOverlay.showing != null`.

---

### Task 1: HelpOverlay

**Files:**
- Create: `src/main/kotlin/com/leeturner/mtui/adapters/inbound/tui/HelpOverlay.kt`
- Test: `src/test/kotlin/com/leeturner/mtui/adapters/inbound/tui/HelpOverlayTest.kt`

**Interfaces:**
- Produces:
  - `data class KeyHelp(val keys: String, val does: String)`
  - `data class Help(val title: String, val keys: List<KeyHelp>)`
  - `enum class HelpResult { PASS, HANDLED, QUIT }`
  - `class HelpOverlay` with `val showing: Help?`, `fun open(help: Help)`, `fun handleKey(event: KeyEvent, available: Help?): HelpResult`, `fun render(behind: Element): Element`

- [ ] **Step 1: Write the failing tests**

```kotlin
class HelpOverlayTest {
    private val pickerHelp = Help("Feature picker keys", listOf(KeyHelp("x", "unselect every feature")))
    private val overlay = HelpOverlay()

    @Test
    fun `question mark opens the help the screen offers`() {
        expectThat(overlay.handleKey(KeyEvent.ofChar('?'), pickerHelp)).isEqualTo(HelpResult.HANDLED)
        expectThat(overlay.showing).isEqualTo(pickerHelp)
    }

    @Test
    fun `question mark passes through where the screen offers no help`() {
        expectThat(overlay.handleKey(KeyEvent.ofChar('?'), null)).isEqualTo(HelpResult.PASS)
        expectThat(overlay.showing).isNull()
    }

    @Test
    fun `other keys pass through while help is closed`() {
        expectThat(overlay.handleKey(KeyEvent.ofChar('x'), pickerHelp)).isEqualTo(HelpResult.PASS)
    }

    @Test
    fun `question mark or Esc closes it`() {
        overlay.open(pickerHelp)
        expectThat(overlay.handleKey(KeyEvent.ofChar('?'), pickerHelp)).isEqualTo(HelpResult.HANDLED)
        expectThat(overlay.showing).isNull()

        overlay.open(pickerHelp)
        expectThat(overlay.handleKey(KeyEvent.ofKey(KeyCode.ESCAPE), pickerHelp)).isEqualTo(HelpResult.HANDLED)
        expectThat(overlay.showing).isNull()
    }

    @Test
    fun `other keys are swallowed while it is open`() {
        overlay.open(pickerHelp)
        listOf(KeyEvent.ofChar('x'), KeyEvent.ofKey(KeyCode.ENTER), KeyEvent.ofChar('j')).forEach {
            expectThat(overlay.handleKey(it, pickerHelp)).isEqualTo(HelpResult.HANDLED)
        }
        expectThat(overlay.showing).isEqualTo(pickerHelp)
    }

    @Test
    fun `Ctrl+C quits while it is open`() {
        overlay.open(pickerHelp)
        expectThat(overlay.handleKey(KeyEvent.ofChar('c', KeyModifiers.CTRL), pickerHelp)).isEqualTo(HelpResult.QUIT)
    }
}
```

Check the exact `KeyCode` and `KeyModifiers` constant names in `tamboui-tui` 0.5.0 (`javap -cp <jar> dev.tamboui.tui.event.KeyCode`) and that `isCancel` / `isCtrlC` are true for these events; adjust the event construction, not the assertions.

- [ ] **Step 2: Run them to verify they fail**

Run: `./gradlew test --tests '*HelpOverlayTest'`
Expected: compilation failure, `HelpOverlay` unresolved.

- [ ] **Step 3: Implement `HelpOverlay.kt`**

- `handleKey`: when showing, `isCtrlC` → `QUIT`; `isChar('?')` or `isCancel` → close, `HANDLED`; anything else → `HANDLED`. When not showing, `isChar('?')` with a non-null `available` → open it, `HANDLED`; otherwise `PASS`.
- `render(behind)`: returns `behind` unchanged when nothing is showing. Otherwise a `stack(...)` of the screen dimmed and a centred `dialog(title, …).rounded()`, the same way `PreviewScreen.fileList()` stacks its dialog over the file view. One row per `KeyHelp`: the keys bold and padded to the longest `keys` in the list, then `does`. Then a blank row and `text("? or Esc to close").dim()`. Size the dialog's width to its longest row plus borders, and its height to its rows, so it doesn't stretch to fill the screen.
- Dimming the screen behind: try `.dim()` on a container around `behind`. If child styles (bold, green, yellow) override it and the screen doesn't look faded, report that in the task summary instead of building a custom dimming widget. The popup over an undimmed screen is acceptable.

- [ ] **Step 4: Run them to verify they pass**

Run: `./gradlew test --tests '*HelpOverlayTest'`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add src/main/kotlin/com/leeturner/mtui/adapters/inbound/tui/HelpOverlay.kt src/test/kotlin/com/leeturner/mtui/adapters/inbound/tui/HelpOverlayTest.kt
git commit --no-gpg-sign -m "Add a help popup that answers keys first"
```

### Task 2: Help on every screen

**Files:**
- Modify: `src/main/kotlin/com/leeturner/mtui/adapters/inbound/tui/MtuiApp.kt` (`render`, `handleKey`)
- Modify: `src/main/kotlin/com/leeturner/mtui/adapters/inbound/tui/OptionsFormScreen.kt` (hint line, `HELP`, name box `?`)
- Modify: `src/main/kotlin/com/leeturner/mtui/adapters/inbound/tui/FeaturePickerScreen.kt` (`HINTS`, `HELP`)
- Modify: `src/main/kotlin/com/leeturner/mtui/adapters/inbound/tui/PreviewScreen.kt` (hints, `help`)
- Test: `src/test/kotlin/com/leeturner/mtui/adapters/inbound/tui/PreviewScreenTest.kt`, `src/test/kotlin/com/leeturner/mtui/adapters/inbound/tui/OptionsFormScreenTest.kt` (create)

**Interfaces:**
- Consumes: Task 1's `Help`, `KeyHelp`, `HelpOverlay`, `HelpResult`.
- Produces:
  - `OptionsFormScreen.HELP: Help` and `FeaturePickerScreen.HELP: Help` (companion, `internal` or public).
  - `PreviewScreen.help: Help` (property: the file list's help while the list is open, otherwise the preview's).
  - `internal fun TextInputState.takeTypedQuestionMark(): Boolean` in `OptionsFormScreen.kt`.

Why the name box needs `takeTypedQuestionMark`: TamboUI's `TextInputElement.handleKeyEvent` inserts every printable key itself and never calls an `onKeyEvent` handler, and `EventRouter` only runs global handlers for keys the focused element left unhandled. So `?` typed in the name box can't be caught before it lands in the text. Instead the form removes it after the fact: if the character just before the cursor is `?`, delete it and return true. A name can't contain `?`, so nothing real is lost.

- [ ] **Step 1: Write the failing tests**

`OptionsFormScreenTest`:

```kotlin
@Test
fun `a question mark just typed in the name is taken out`() {
    val name = TextInputState("demo")
    name.insert('?')
    expectThat(name.takeTypedQuestionMark()).isTrue()
    expectThat(name.text()).isEqualTo("demo")
}

@Test
fun `a question mark typed mid-name is taken out where it was typed`() {
    val name = TextInputState("demoapp")
    repeat(3) { name.moveCursorLeft() }
    name.insert('?')
    expectThat(name.takeTypedQuestionMark()).isTrue()
    expectThat(name.text()).isEqualTo("demoapp")
    expectThat(name.cursorPosition()).isEqualTo(4)
}

@Test
fun `a name without a question mark before the cursor is left alone`() {
    val name = TextInputState("demo")
    expectThat(name.takeTypedQuestionMark()).isFalse()

    val pasted = TextInputState()
    pasted.insert("demo?x")
    expectThat(pasted.takeTypedQuestionMark()).isFalse()
    expectThat(pasted.text()).isEqualTo("demo?x")
}
```

Check what cursor position `TextInputState(String)` starts at; if it isn't the end, call `moveCursorToEnd()` first in the tests.

`PreviewScreenTest` (add, using a two-file `ProjectPreview` like `FileBrowserTest`'s):

```kotlin
@Test
fun `the file list has its own help and keeps its filter when help closes`() {
    val screen = PreviewScreen(FileBrowser(preview))
    expectThat(screen.help.title).isEqualTo("Preview keys")

    screen.handleKey(KeyEvent.ofChar('f'))
    screen.handleKey(KeyEvent.ofChar('R'))
    expectThat(screen.help.title).isEqualTo("File list keys")

    val overlay = HelpOverlay()
    overlay.handleKey(KeyEvent.ofChar('?'), screen.help)
    overlay.handleKey(KeyEvent.ofKey(KeyCode.ESCAPE), screen.help)
    expectThat(screen.help.title).isEqualTo("File list keys")
}
```

Assert the filter survived through whatever `FileBrowser` exposes (its `filter` property or `matches`).

- [ ] **Step 2: Run them to verify they fail**

Run: `./gradlew test --tests '*OptionsFormScreenTest' --tests '*PreviewScreenTest'`
Expected: compilation failure, `takeTypedQuestionMark` and `help` unresolved.

- [ ] **Step 3: Add the help lists and hint lines**

Each `Help` sits in its screen's companion next to the hint line. Exact copy:

`OptionsFormScreen.HELP`, title `Name and options keys`:

| keys | does |
|------|------|
| `Tab / Shift+Tab` | move between the name and the options |
| `j / ↓` | move down the options |
| `k / ↑` | move up the options |
| `h l / ← →` | change the option |
| `p` | preview the generated files |
| `Enter` | choose features |
| `Esc` | quit |
| `Ctrl+C` | quit |

`FeaturePickerScreen.HELP`, title `Feature picker keys`:

| keys | does |
|------|------|
| `j / ↓` | move down |
| `k / ↑` | move up |
| `Space` | select or unselect the feature |
| `/` | search names, titles and descriptions |
| `Enter / Esc` | leave the search |
| `Esc` | clear the search, then go back |
| `x` | unselect every feature |
| `p` | preview the generated files |
| `Enter` | generate the project |
| `Ctrl+C` | quit |

`PreviewScreen`, title `Preview keys`:

| keys | does |
|------|------|
| `j / ↓` | scroll down |
| `k / ↑` | scroll up |
| `PgDn / PgUp` | scroll a page |
| `[ / ]` | previous or next file |
| `f / /` | open the file list |
| `Esc` | go back |
| `Ctrl+C` | quit |

`PreviewScreen`, title `File list keys`:

| keys | does |
|------|------|
| type | filter the files |
| `↑ / ↓` | move |
| `Enter` | open the file |
| `Esc` | close the list |
| `Ctrl+C` | quit |

Hint lines become:
- form: `Tab/j/k move · ←/→ h/l change · p preview · Enter choose features · Esc quit · ? help`
- picker: `j/k move · Space toggle · / search · x clear · p preview · Enter generate · Esc back · ? help`
- preview: `j/k scroll · f files · [ ] prev/next · Esc back · ? help`
- file list: `type to filter · ↑/↓ move · Enter open · Esc close · ? help`

Before finalising, re-read each screen's `handleKey` and add any key it handles that the table misses.

- [ ] **Step 4: Implement `takeTypedQuestionMark` and `PreviewScreen.help`**

`takeTypedQuestionMark`: if `text().getOrNull(cursorPosition() - 1) == '?'`, `deleteBackward()` and return true; otherwise false. It looks only at the character before the cursor, so a pasted `demo?x` is left for the name validation to reject.

- [ ] **Step 5: Wire it into `MtuiApp`**

- Add `private val help = HelpOverlay()`.
- `availableHelp(): Help?`: `OptionsFormScreen.HELP` on a `Screen.Form` that isn't `busy`, `FeaturePickerScreen.HELP` on a `Screen.Picker` that isn't `previewing`, `current.screen.help` on a `Screen.Preview`, otherwise null.
- `handleKey`: before the existing `when`, `when (help.handleKey(event, availableHelp()))`: `QUIT` → `finish(MtuiOutcome.Cancelled)`; `HANDLED` → return `EventResult.HANDLED`; `PASS` → carry on to the existing code. A `?` typed in the picker's search box never reaches here, because the focused search box consumes it, so it is typed as the spec wants.
- `render`: on the `Screen.Form` branch, when not `busy` and `formScreen.nameInput.takeTypedQuestionMark()`, call `help.open(OptionsFormScreen.HELP)` before rendering. Wrap the chosen content in `help.render(content)` inside the existing root `column(...)`, so focus and the root key handler don't change.
- Don't touch focus when opening or closing help, so the screen comes back exactly as it was.

- [ ] **Step 6: Run the tests**

Run: `./gradlew test --tests '*OptionsFormScreenTest' --tests '*PreviewScreenTest' --tests '*HelpOverlayTest'`
Expected: PASS

- [ ] **Step 7: Check it in the real app**

Run: `./gradlew shadowJar && java -jar build/libs/micronaut-launch-tui-0.1-all.jar` in a scratch directory (or drive it with a throwaway vhs tape and look at the screenshot). Check:
- `?` in the empty name box opens "Name and options keys", and `Esc` closes it without quitting.
- `demo?` in the name box opens help and leaves `demo`.
- `?` on an option row, the picker list, the preview and the file list each open the right popup.
- `?` in the picker search types `?`.
- `j` while help is open does nothing to the list behind.
- `Ctrl+C` with help open quits.

- [ ] **Step 8: Run the whole build**

Run: `./gradlew build`
Expected: BUILD SUCCESSFUL (tests, Kotlinter, Detekt, ArchUnit).

- [ ] **Step 9: Commit**

```bash
git add src/main/kotlin/com/leeturner/mtui/adapters/inbound/tui src/test/kotlin/com/leeturner/mtui/adapters/inbound/tui
git commit --no-gpg-sign -m "Show each screen's keys with ?"
```

### Task 3: Screenshots and README

**Files:**
- Create: `docs/tapes/config.tape`, `docs/tapes/demo.tape`, `docs/tapes/form.tape`, `docs/tapes/picker.tape`, `docs/tapes/preview.tape`, `docs/tapes/help.tape`
- Create: `docs/images/demo.gif`, `docs/images/form.png`, `docs/images/picker.png`, `docs/images/preview.png`, `docs/images/help.png` (generated)
- Modify: `README.md` (rewrite)
- Modify: `docs/roadmap.md` (slice 7 status `Done`, as slice 6 did in its last commit)

**Interfaces:**
- Consumes: the help popup from Task 2 (for `help.png`), the shadow jar `build/libs/micronaut-launch-tui-0.1-all.jar`.

- [ ] **Step 1: Write the tapes**

- `config.tape`: shared `Set` lines (shell, font size, width, height, theme, typing speed) and a hidden setup that saves the jar's absolute path, `cd`s into a fresh `mktemp -d`, and defines `alias mtui='java -jar <jar>'`, then clears the screen. Every other tape starts with `Source docs/tapes/config.tape`, so all the images look the same and none of them leave a project in the repo.
- Each tape types `mtui`, waits on screen text with `Wait+Screen /…/` rather than fixed sleeps (Launch's response time varies), drives to its screen, and ends with `Screenshot docs/images/<name>.png` (stills) or `Output docs/images/demo.gif` (the GIF).
- `demo.gif`: type `demo`, Enter, `/`, type `data-jdbc`, Enter, Space, Enter to generate, wait for the `cd demo` line. Keep it short (under about 20 seconds).
- `form.png`: name typed, an option row focused.
- `picker.png`: a search for `data` with two or three features selected.
- `preview.png`: the preview from the picker, showing `build.gradle.kts`.
- `help.png`: the picker with its help popup open.
- Tapes are run from the repo root, so paths in them are relative to it.

- [ ] **Step 2: Generate the images**

Run: `./gradlew shadowJar && for t in docs/tapes/{demo,form,picker,preview,help}.tape; do vhs "$t"; done`
Expected: five files in `docs/images/`; open each and check it shows the intended screen with no stray shell output. `git status` shows no generated project anywhere in the repo.

- [ ] **Step 3: Rewrite `README.md`**

Sections in the spec's order (Part 2, items 1–10), using the spec's wording for what each covers. Specifics:
- Build and run: Java 25, `./gradlew shadowJar`, `java -jar build/libs/micronaut-launch-tui-0.1-all.jar`, a note that prebuilt downloads come later.
- Shell access: `alias mtui='java -jar /path/to/micronaut-launch-tui-0.1-all.jar'` in `~/.zshrc` or `~/.bashrc`, then `mkdir my-app && cd my-app && mtui`. Say the project unzips into the current directory.
- Opening the project: `mtui --open idea` and `export MTUI_OPEN=idea`; `--open` wins.
- Development: `./gradlew build` (tests, Kotlinter, Detekt), `./gradlew formatKotlin`, links to `docs/roadmap.md` and `docs/superpowers/specs/`, and the regenerate command from Step 2.
- Remove all template text.

- [ ] **Step 4: Check the README**

Run: `grep -n "template\|Micronaut CLI Template" README.md` → no matches. Check every image path in the README exists: `grep -o 'docs/images/[^)]*' README.md | xargs ls`.

- [ ] **Step 5: Commit**

```bash
git add docs/tapes docs/images README.md docs/roadmap.md
git commit --no-gpg-sign -m "Rewrite the README with screenshots made by vhs"
```

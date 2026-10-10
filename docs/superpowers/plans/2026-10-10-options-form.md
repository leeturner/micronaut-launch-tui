# Options Form Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** On the name screen the user chooses type, language, build, test framework and JDK. Features are fetched for the chosen type and language, and `/create` is called with all five options.

**Architecture:** This follows the existing hexagonal layout. A new domain model, `ProjectOptions`, holds the five choices. It replaces passing `SelectOptions.defaultType` around and knows how to apply a language's defaults. `CatalogLoader` splits into `loadOptions()`, called at start-up, and `loadFeatures(type, language)`, called on Enter. The form's state is a plain class, `OptionsForm`, kept free of TamboUI like `FeaturePicker`. `MtuiApp` renders it.

**Tech Stack:** Kotlin 2.4, Micronaut 5.2 + Picocli, generated Micronaut OpenAPI client, Arrow `Either`, TamboUI 0.5.0 toolkit, JUnit 5, Strikt, WireMock (`io.github.leeturner:wiremock-micronaut`).

**Spec:** `docs/superpowers/specs/2026-10-10-options-form-design.md`

## Global Constraints

- Errors are typed with Arrow `Either`. Never throw across a port.
- Every list moves with the arrow keys **and** `j`/`k`. Letter keys only act as commands on an option row or a list, never while a text field has focus.
- Changing the language moves Test and Build to that language's defaults. If a language has no defaults, they are left alone.
- ←/→ and `h`/`l` stop at the first and last value. They don't wrap.
- All five options are always sent to `/create`. `mtui` never checks combinations itself. (Checked against real Launch on 2026-10-10: it accepts `javaVersion=JDK_17` and also accepts Kotest with Java, returning 201.)
- Nothing is remembered between runs.
- No new dependencies.
- Gradle runs on Java 25 (`.sdkmanrc`). `./gradlew check` (tests + kotlinter + detekt) passes after every task. Commit with `git commit --no-gpg-sign`.

## Review Focus

1. **Typing letters in the name box:** `h`, `j`, `k` and `l` typed into the name box must appear in the name. They must not move focus or change an option. (Task 6 manual check.)
2. **Language defaults Launch doesn't list:** if a language's default test or build value isn't among the offered test frameworks or build types, that option stays as it was and nothing crashes. (Task 1 test.)
3. **Changing the language and changing it back:** Java → Kotlin → Java followed by Enter must not fetch features again. The picker reopens as it was left. Compare against the type and language the features were loaded for, not a "changed" flag. (Task 6 manual check.)
4. **Enter pressed again while features are loading:** nothing happens and there is no second fetch. After a failed fetch, Enter tries again. (Task 5 manual check.)
5. **Selected features after a new fetch:** names missing from the new list are dropped, the rest keep their order, and a duplicate name passed in is not selected twice. (Task 3 test.)

---

### Task 1: `ProjectOptions` domain model

**Files:**
- Create: `src/main/kotlin/com/leeturner/mtui/domain/core/model/ProjectOptions.kt`
- Test: `src/test/kotlin/com/leeturner/mtui/domain/core/model/ProjectOptionsTest.kt`

**Interfaces:**
- Produces:
  - `data class ProjectOptions(val type: ApplicationType, val language: Language, val build: BuildType, val test: TestFramework, val jdk: JdkVersion)`
  - `companion object { fun defaultsFrom(options: SelectOptions): ProjectOptions }`, which uses each `default*` field.
  - `fun ProjectOptions.withLanguage(language: Language, options: SelectOptions): ProjectOptions` sets the language. If `language.defaults` is not null, it also sets `test` to the `options.testFrameworks` entry whose `value == defaults.test`, and `build` to the `options.buildTypes` entry whose `value == defaults.build`. Each is left unchanged when no entry matches.

- [ ] **Step 1: Write the failing tests**

Build a `SelectOptions` fixture with Java (defaults `JUNIT`/`GRADLE_KOTLIN`), Groovy (defaults `SPOCK`/`GRADLE`), and Kotlin with `defaults = null`. Include test frameworks JUnit, Spock and Kotest, and build types Gradle, Gradle Kotlin and Maven. Make the defaults Java, Gradle Kotlin and JUnit.

```kotlin
@Test fun `defaults come from launch's defaults`()
// expect ProjectOptions.defaultsFrom(options) == ProjectOptions(defaultType, java, gradleKotlin, junit, defaultJdk)

@Test fun `changing language applies its test and build defaults`()
// defaults.copy(test = kotest, build = maven).withLanguage(groovy, options) → language groovy, test spock, build gradle

@Test fun `a language with no defaults leaves test and build alone`()
// defaults.copy(test = kotest, build = maven).withLanguage(kotlin, options) → test kotest, build maven

@Test fun `defaults launch doesn't offer leave test and build alone`()
// a language with defaults LanguageDefaults(test = "PYTEST", build = "PYRONAUT") → test and build unchanged
```

- [ ] **Step 2: Run the tests and check they fail**

Run: `./gradlew test --tests '*ProjectOptionsTest'`
Expected: FAIL to compile, because `ProjectOptions` doesn't exist yet.

- [ ] **Step 3: Implement `ProjectOptions`, `defaultsFrom` and `withLanguage` in `ProjectOptions.kt`**

- [ ] **Step 4: Run the tests and check they pass**

Run: `./gradlew test --tests '*ProjectOptionsTest'`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add src/main/kotlin/com/leeturner/mtui/domain/core/model/ProjectOptions.kt src/test/kotlin/com/leeturner/mtui/domain/core/model/ProjectOptionsTest.kt
git commit --no-gpg-sign -m "Add the chosen project options"
```

---

### Task 2: Send the chosen options to `/create`

**Files:**
- Modify: `src/main/openapi/micronaut-launch-5.2.2.yml` (the `javaVersion` parameter of `createApp`, around line 136)
- Modify: `src/main/kotlin/com/leeturner/mtui/domain/core/ports/ProjectCreator.kt`
- Modify: `src/main/kotlin/com/leeturner/mtui/domain/core/services/ProjectGenerator.kt`
- Modify: `src/main/kotlin/com/leeturner/mtui/adapters/outbound/http/MicronautLaunchProjectCreator.kt`
- Modify: `src/main/kotlin/com/leeturner/mtui/adapters/inbound/tui/MtuiApp.kt` (the `generate` call only)
- Test: `src/test/kotlin/com/leeturner/mtui/adapters/outbound/http/MicronautLaunchProjectCreatorTest.kt`
- Test: `src/test/kotlin/com/leeturner/mtui/domain/core/services/ProjectGeneratorTest.kt`

**Interfaces:**
- Consumes: `ProjectOptions` (Task 1).
- Produces:
  - `ProjectCreator.createProject(options: ProjectOptions, name: ProjectName, features: List<String>): Either<GenerateProjectError, ByteArray>`
  - `ProjectGenerator.generate(options: ProjectOptions, name: ProjectName, features: List<String>, into: Path): Either<GenerateProjectError, Path>`

- [ ] **Step 1: Patch the OpenAPI spec**

Replace the `javaVersion` parameter's `allOf: [$ref JdkVersion]` schema with a plain nullable string. Add a comment in the same style as the existing patch:

```yaml
        schema:
          # mtui patch: upstream refs the JdkVersion object, but Launch takes the enum value, e.g. JDK_21
          type: string
          nullable: true
```

The generated `createApp` then takes `javaVersion: String?`.

- [ ] **Step 2: Update the tests to the new signatures and add the failing ones**

In both test classes, replace `type` with a `ProjectOptions` fixture: type `DEFAULT`, language `KOTLIN`, build `GRADLE_KOTLIN`, test `KOTEST`, JDK `JDK_21`. Existing tests otherwise stay as they are. The "unknown type" test becomes `options.copy(type = ...SERVERLESS)`.

```kotlin
// MicronautLaunchProjectCreatorTest
@Test fun `chosen options are sent to launch`()
// stubCreate 201; createProject(options, ...); then
// wireMock.verify(getRequestedFor(urlPathEqualTo("/create/DEFAULT/com.example.my-app"))
//     .withQueryParam("lang", equalTo("KOTLIN")).withQueryParam("build", equalTo("GRADLE_KOTLIN"))
//     .withQueryParam("test", equalTo("KOTEST")).withQueryParam("javaVersion", equalTo("JDK_21")))

@Test fun `language unknown to the client is an unexpected error`()
// options.copy(language = ...value = "PYTHON2") → UnexpectedProjectCreationError, message "Unsupported language: PYTHON2"

@Test fun `test framework unknown to the client is an unexpected error`()
// value "TESTNG" → message "Unsupported test framework: TESTNG"

@Test fun `build type unknown to the client is an unexpected error`()
// value "ANT" → message "Unsupported build type: ANT"

// ProjectGeneratorTest: rename `features are passed to launch` → `options and features are passed to launch`
// FakeCreator records lastOptions too; expect lastOptions == options
```

- [ ] **Step 3: Run the tests and check they fail**

Run: `./gradlew test --tests '*MicronautLaunchProjectCreatorTest' --tests '*ProjectGeneratorTest'`
Expected: FAIL to compile, because the signatures still take `ApplicationType`.

- [ ] **Step 4: Implement**

- `ProjectCreator` and `ProjectGenerator` take `options: ProjectOptions` in place of `type`.
- `MicronautLaunchProjectCreator` maps each value through the generated enums: `MicronautLaunchApplicationType`, `MicronautLaunchLanguage`, `MicronautLaunchBuildTool` and `MicronautLaunchTestFramework1`. Use their `VALUE_MAPPING`, and raise `UnexpectedProjectCreationError(null, "Unsupported <thing>: <value>")` when one is missing, as is already done for the type. It passes `options.jdk.value` as `javaVersion`.
- In `MtuiApp.generate`, pass `ProjectOptions.defaultsFrom(options)` for now. Task 6 replaces this.

- [ ] **Step 5: Run the full build**

Run: `./gradlew check`
Expected: BUILD SUCCESSFUL

- [ ] **Step 6: Commit**

```bash
git add -A src
git commit --no-gpg-sign -m "Send the chosen options to Launch"
```

---

### Task 3: Keep the selection when the feature list changes

**Files:**
- Modify: `src/main/kotlin/com/leeturner/mtui/adapters/inbound/tui/FeaturePicker.kt`
- Modify: `src/main/kotlin/com/leeturner/mtui/adapters/inbound/tui/FeaturePickerScreen.kt` (constructor only)
- Test: `src/test/kotlin/com/leeturner/mtui/adapters/inbound/tui/FeaturePickerTest.kt`

**Interfaces:**
- Produces:
  - `class FeaturePicker(features: List<Feature>, selected: List<String> = emptyList())` starts with the names in `selected` that exist in `features`, in `selected` order.
  - `class FeaturePickerScreen(features: List<Feature>, listFocusId: String, selected: List<String> = emptyList())` passes `selected` through.

- [ ] **Step 1: Write the failing tests**

```kotlin
@Test fun `starting selection keeps features that exist, in order`()
// FeaturePicker(listOf(postgres, ksp, jdbc, kapt), selected = listOf("ksp", "gone", "data-jdbc")).selected
//   == listOf("ksp", "data-jdbc")

@Test fun `a name passed twice is selected once`()
// selected = listOf("ksp", "ksp") → selected == listOf("ksp")
```

- [ ] **Step 2: Run the tests and check they fail**

Run: `./gradlew test --tests '*FeaturePickerTest'`
Expected: FAIL to compile, because there is no `selected` parameter.

- [ ] **Step 3: Add the parameter and fill `chosen` from it in the `init` block**

`chosen` is already a `LinkedHashSet`, so duplicates and order are handled.

- [ ] **Step 4: Run the tests and check they pass**

Run: `./gradlew test --tests '*FeaturePickerTest'`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add src/main/kotlin/com/leeturner/mtui/adapters/inbound/tui/FeaturePicker*.kt src/test/kotlin/com/leeturner/mtui/adapters/inbound/tui/FeaturePickerTest.kt
git commit --no-gpg-sign -m "Keep selected features when the feature list changes"
```

---

### Task 4: `OptionsForm` state

**Files:**
- Create: `src/main/kotlin/com/leeturner/mtui/adapters/inbound/tui/OptionsForm.kt`
- Test: `src/test/kotlin/com/leeturner/mtui/adapters/inbound/tui/OptionsFormTest.kt`

**Interfaces:**
- Consumes: `ProjectOptions`, `ProjectOptions.defaultsFrom` and `withLanguage` (Task 1).
- Produces:
  - `enum class OptionRow(val label: String) { TYPE("Type"), LANGUAGE("Language"), BUILD("Build"), TEST("Test"), JDK("JDK") }`
  - `data class Choice(val label: String, val chosen: Boolean)`
  - `class OptionsForm(options: SelectOptions)` with:
    - `val chosen: ProjectOptions`, which starts at `ProjectOptions.defaultsFrom(options)` and has a private setter.
    - `fun choices(row: OptionRow): List<Choice>`, every value in Launch's order. Type uses the short label, and the other rows use Launch's `label`.
    - `fun next(row: OptionRow)` and `fun previous(row: OptionRow)`. They stop at the ends. Changing `LANGUAGE` goes through `withLanguage`.
  - Short type labels: a private map `DEFAULT→Default, CLI→CLI, FUNCTION→Function, GRPC→gRPC, MESSAGING→Messaging`. A value that isn't in the map falls back to Launch's `label`.

- [ ] **Step 1: Write the failing tests**

Use a fixture shaped like the recorded `get-select-options.json`: all five types, Java, Groovy (defaults Spock/Gradle Kotlin) and Kotlin, three builds, three test frameworks and three JDKs, with the defaults DEFAULT, Java, Gradle Kotlin, JUnit and JDK 21.

```kotlin
@Test fun `starts on launch's defaults`()
// choices(LANGUAGE) == listOf(Choice("Java", true), Choice("Groovy", false), Choice("Kotlin", false))

@Test fun `type uses short labels`()
// choices(TYPE).map { it.label } == listOf("Default", "CLI", "Function", "gRPC", "Messaging")

@Test fun `unknown type falls back to launch's label`()

@Test fun `next and previous move the choice and stop at the ends`()
// JDK starts on 21: next → 25, next → 25, previous ×3 → 17

@Test fun `changing language applies its defaults`()
// next(LANGUAGE) → Groovy; chosen.test.value == "SPOCK"
```

- [ ] **Step 2: Run the tests and check they fail**

Run: `./gradlew test --tests '*OptionsFormTest'`
Expected: FAIL to compile, because `OptionsForm` doesn't exist yet.

- [ ] **Step 3: Implement `OptionsForm`**

- [ ] **Step 4: Run the tests and check they pass**

Run: `./gradlew test --tests '*OptionsFormTest'`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add src/main/kotlin/com/leeturner/mtui/adapters/inbound/tui/OptionsForm.kt src/test/kotlin/com/leeturner/mtui/adapters/inbound/tui/OptionsFormTest.kt
git commit --no-gpg-sign -m "Add options form state"
```

---

### Task 5: Fetch features on Enter instead of at start-up

**Files:**
- Modify: `src/main/kotlin/com/leeturner/mtui/domain/core/services/CatalogLoader.kt`
- Rename: `src/main/kotlin/com/leeturner/mtui/domain/core/model/Catalog.kt` → `CatalogError.kt` (delete `Catalog`, keep `CatalogError`)
- Modify: `src/main/kotlin/com/leeturner/mtui/adapters/inbound/tui/MtuiApp.kt`
- Test: `src/test/kotlin/com/leeturner/mtui/domain/core/services/CatalogLoaderTest.kt`

**Interfaces:**
- Consumes: `FeaturePickerScreen(features, listFocusId, selected)` (Task 3), `ProjectOptions` (Task 1).
- Produces:
  - `CatalogLoader.loadOptions(): Either<CatalogError, SelectOptions>`
  - `CatalogLoader.loadFeatures(type: ApplicationType, language: Language): Either<CatalogError, List<Feature>>`

- [ ] **Step 1: Rewrite the `CatalogLoader` tests**

```kotlin
@Test fun `options are loaded`()                       // loadOptions() is Right(options); features not requested
@Test fun `options failure is reported`()              // Left(CatalogError("No application types"))
@Test fun `features are loaded for the given type and language`() // loadFeatures(cli, kotlin) → Right(listOf(ksp)); requested == cli to kotlin
@Test fun `features failure is reported`()             // Left(CatalogError("Server Error"))
```

- [ ] **Step 2: Run the tests and check they fail**

Run: `./gradlew test --tests '*CatalogLoaderTest'`
Expected: FAIL to compile.

- [ ] **Step 3: Implement the split and change `MtuiApp` to use it**

- `onStart` only calls `loadOptions()`. The loading screen and the "any key exits" failure are unchanged. The loading text says "Fetching options…".
- Enter on the form (`submitName`): check the name and folder as now, then compare `ProjectOptions.defaultsFrom(options)`'s type and language (Task 6 swaps this for the form's choice) with the type and language the current features were loaded for. Keep that pair in a field.
  - Same pair: open the picker straight away. Its search, highlight and selection are kept.
  - Different pair or nothing loaded yet: show a spinner with "Fetching features…" on the form, and ignore keys while it shows. Then call `loadFeatures` with `background(...)`.
    - On success: `pickerScreen = FeaturePickerScreen(features, ROOT_ID, pickerScreen.picker.selected)`, record the pair, and open the picker. The new screen starts with an empty search, which is what the spec asks for.
    - On failure: return to the form with the error in the existing error line, and keep the name.
- `pickerScreen` starts as an empty `FeaturePickerScreen(emptyList(), ROOT_ID)`, as now.

- [ ] **Step 4: Run the full build**

Run: `./gradlew check`
Expected: BUILD SUCCESSFUL

- [ ] **Step 5: Manual check against real Launch** (`./gradlew run`, or the run skill)

- Start-up shows the form without fetching features.
- Enter shows "Fetching features…" and then the picker. Esc, then Enter again, reopens the picker immediately with the selection kept.
- Turn off the network, use a fresh name, and press Enter: the error shows on the form. Turn the network back on and press Enter: the picker opens.
- Press Enter twice quickly: there is only one fetch, and no error.

- [ ] **Step 6: Commit**

```bash
git add -A src
git commit --no-gpg-sign -m "Fetch features for the chosen type and language on Enter"
```

---

### Task 6: Editable options on the name screen

**Files:**
- Modify: `src/main/kotlin/com/leeturner/mtui/adapters/inbound/tui/MtuiApp.kt`
- Modify: `src/main/kotlin/com/leeturner/mtui/adapters/inbound/tui/FeaturePickerScreen.kt` (`render` and `summary`)

**Interfaces:**
- Consumes: `OptionsForm`, `OptionRow` and `Choice` (Task 4). Task 5's fetch-on-Enter.
- Produces: `FeaturePickerScreen.render(options: ProjectOptions, name: ProjectName, status: StyledElement<*>, focus: FocusManager)`. The header summary is built from `ProjectOptions`: `type.label · language.label · build.label · test.label · jdk.label`.

- [ ] **Step 1: Wire `OptionsForm` into `MtuiApp`**

- Create one `OptionsForm` when the options load, and keep it for the whole run so going back keeps the choices. `Screen.Picker` and `Screen.Generating` carry `ProjectOptions` (from `optionsForm.chosen`) instead of `SelectOptions`. Task 5's comparison and Task 2's `generate` call use `optionsForm.chosen`.
- Render the five rows under the name box, as in the spec's mockup: the row label, then each `Choice` as `(•) label` or `( ) label`. The focused row is highlighted. The footer reads `Tab/j/k move · ←/→ h/l change · Enter choose features · Esc quit`.
- Make each row a focusable element with the id `option-<row>`, so that TamboUI's own Tab/Shift+Tab handling (`EventRouter` → `FocusManager.focusNext/focusPrevious`) moves through the name box and the rows in render order. The root key handler maps the focused id back to an `OptionRow`.
- Keys on a focused row: `j`/↓ focus the next row, and on JDK they do nothing. `k`/↑ focus the previous row, and on Type they focus the name box. `h`/← call `previous(row)`, `l`/→ call `next(row)`, Enter submits and Esc quits.
- In the name box, ↓ focuses Type. Every other key behaves as now.
- When Enter fails with a name error, focus the name box.

- [ ] **Step 2: Show the chosen options in the picker header**

Change `render` and `summary` to take `ProjectOptions`.

- [ ] **Step 3: Run the full build**

Run: `./gradlew check`
Expected: BUILD SUCCESSFUL

- [ ] **Step 4: Manual check against real Launch**

- Type `hjkl` in the name box: the letters appear, and focus and options don't change.
- Tab and Shift+Tab move through the name box and the five rows. Check that Tab never lands on an invisible element. If the root column takes a Tab stop, make it non-focusable while the form shows.
- Move to Language and press `l` twice to choose Kotlin: Test and Build move to Kotlin's defaults.
- Press `l` on the last value and `h` on the first: nothing changes.
- Choose Kotlin, then Enter: the header shows Kotlin, and searching for "kotlin" finds kapt and ksp. Select kapt and generate. The project's `build.gradle.kts` uses Kotlin and kapt, and `jdkVersion` matches the chosen JDK.
- Go back, switch to Java and press Enter: features are fetched again, and kapt is dropped if Java's list doesn't have it. Switch Java → Kotlin → Java, then Enter: nothing is fetched.
- Enter an invalid name with options changed: the error shows, the name box has focus, and the options are kept.

- [ ] **Step 5: Commit**

```bash
git add -A src
git commit --no-gpg-sign -m "Make the options editable on the name screen"
```

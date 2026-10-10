# Feature Picker Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** After the name screen, the user picks features from a searchable list grouped by category, using vim keys, and `/create` is called with the features they picked.

**Architecture:** This follows the slice 1 hexagonal layout. A new outbound port, `FeatureRetriever`, has a Launch HTTP adapter. `ProjectCreator` and `ProjectGenerator` gain a `features` parameter. The picker's behaviour (search, grouping, highlight, selection) is a plain Kotlin class, `FeaturePicker`, kept out of TamboUI so it can be unit tested. `MtuiApp` gains a picker screen that renders it.

**Tech Stack:** Kotlin 2.4, Micronaut 5.2 + Picocli, generated Micronaut OpenAPI client, Arrow `Either`, TamboUI 0.5.0 toolkit, JUnit 5, Strikt, WireMock (`io.github.leeturner:wiremock-micronaut`).

**Spec:** `docs/superpowers/specs/2026-10-10-feature-picker-design.md`

## Global Constraints

- Errors are typed with Arrow `Either`. Never throw across a port.
- Every list moves with the arrow keys **and** `j`/`k`. Letter keys only act as commands when focus is on a list, never while a text field has focus.
- Search is a case-insensitive substring match on name, title and description. No typo tolerance.
- `x` clears all selected features with no confirmation.
- Any 400 from `/create` is shown on the picker, using Launch's message, and the selection is kept.
- No new dependencies.
- `./gradlew check` (tests + kotlinter + detekt) passes after every task. Commit with `git commit --no-gpg-sign`.

## Review Focus

1. **Typing a command letter in the search box:** typing `jdbc`, `x` or `k` into the search box must type those letters, not move, clear or toggle. (Task 4 manual check.)
2. **Search text with spaces or regex characters:** `"  jdbc "` matches the same as `"jdbc"`, and `"."`, `"c++"` or `"["` are matched literally and never throw. (Task 3 tests.)
3. **Moving past either end, or nothing matching:** `j` on the last feature and `k` on the first stay put. When nothing matches, moving and Space do nothing, and nothing crashes. (Task 3 tests.)
4. **Launch sends a feature with missing fields:** a feature with no title, description or category must not crash the load. A missing category becomes `Other`, and a feature with no name is skipped. (Task 1 test.)
5. **Selection hidden by the filter:** a hidden selection stays selected and is still listed by `selected`. `x` clears hidden ones too. (Task 3 tests.)

---

### Task 1: Feature retrieval

**Files:**
- Create: `src/main/kotlin/com/leeturner/mtui/domain/core/model/Feature.kt`
- Create: `src/main/kotlin/com/leeturner/mtui/domain/core/ports/FeatureRetriever.kt`
- Create: `src/main/kotlin/com/leeturner/mtui/adapters/outbound/http/MicronautLaunchFeatureRetriever.kt`
- Create: `src/test/resources/payloads/get-features-default-java.json`
- Create: `src/test/resources/payloads/get-features-missing-fields.json`
- Test: `src/test/kotlin/com/leeturner/mtui/adapters/outbound/http/MicronautLaunchFeatureRetrieverTest.kt`

**Interfaces:**
- Produces:
  - `data class Feature(val name: String, val title: String, val description: String, val category: String, val preview: Boolean, val community: Boolean)` in `Feature.kt`
  - `data class FeaturesError(val status: Int?, val message: String)` in `Feature.kt`
  - `interface FeatureRetriever { fun getFeatures(type: ApplicationType, language: Language): Either<FeaturesError, List<Feature>> }`
  - `@Singleton class MicronautLaunchFeatureRetriever(api: MicronautLaunchDefaultApi) : FeatureRetriever`

- [ ] **Step 1: Record the payloads**

```bash
curl -s https://launch.micronaut.io/application-types/DEFAULT/features/JAVA | python3 -m json.tool > src/test/resources/payloads/get-features-default-java.json
```

Write `get-features-missing-fields.json` by hand: `{"features":[{"name":"bare"},{"title":"No name"}]}`.

- [ ] **Step 2: Write the failing tests**

Use the same `@MicronautTest` / `@EnableWireMock` setup as `MicronautLaunchSelectOptionRetrieverTest`. Stub `GET /application-types/DEFAULT/features/JAVA`. Build `type` with `value = "DEFAULT"` and `language` with `value = "JAVA"`. `okJson(path)` is a local helper that reads the resource and passes it to `WireMock.okJson`.

```kotlin
@Test
fun `200 maps launch features`() {
    stubFeatures(okJson("/payloads/get-features-default-java.json"))

    expectThat(retriever.getFeatures(type, language)).isRight().get { value }.and {
        get { first { it.name == "data-jdbc" } }.isEqualTo(
            Feature("data-jdbc", "Micronaut Data JDBC", "Adds support for Micronaut Data JDBC", "Database", preview = false, community = false),
        )
        get { first { it.name == "buildless" }.community }.isTrue()
        get { first { it.name == "control-panel" }.preview }.isTrue()
    }
}

@Test
fun `missing fields are defaulted and nameless features skipped`() {
    stubFeatures(okJson("/payloads/get-features-missing-fields.json"))

    expectThat(retriever.getFeatures(type, language)).isRight().get { value }
        .isEqualTo(listOf(Feature("bare", "", "", "Other", preview = false, community = false)))
}

@Test
fun `500 is an error with its status`()      // FeaturesError.status == 500

@Test
fun `connection failure is an error`()       // Fault.CONNECTION_RESET_BY_PEER → FeaturesError.status == null

@Test
fun `language unknown to the client is an error`()
// language.copy(value = "COBOL") → FeaturesError(null, "Unsupported language: COBOL"), no HTTP call
```

- [ ] **Step 3: Run the tests to verify they fail**

Run: `./gradlew test --tests '*MicronautLaunchFeatureRetrieverTest'`
Expected: FAIL to compile, because `Feature` and `MicronautLaunchFeatureRetriever` don't exist yet.

- [ ] **Step 4: Implement the model, the port and the adapter**

Call `api.featuresByLanguage(MicronautLaunchApplicationType.VALUE_MAPPING[type.value], MicronautLaunchLanguage.VALUE_MAPPING[language.value])`. An unmapped type or language raises `FeaturesError(null, "Unsupported application type: X")` or `FeaturesError(null, "Unsupported language: X")`. Catch `HttpClientException` the same way `MicronautLaunchSelectOptionRetriever` does. Null `features` maps to an empty list. Null title or description becomes `""`, null category becomes `"Other"`, a null flag becomes `false`, and a null name drops the feature.

- [ ] **Step 5: Run the tests to verify they pass**

Run: `./gradlew test --tests '*MicronautLaunchFeatureRetrieverTest'`
Expected: PASS

- [ ] **Step 6: Commit**

```bash
git add src/main/kotlin/com/leeturner/mtui/domain/core src/main/kotlin/com/leeturner/mtui/adapters/outbound/http/MicronautLaunchFeatureRetriever.kt src/test
git commit --no-gpg-sign -m "Retrieve features from Launch"
```

---

### Task 2: Send selected features to `/create`

**Files:**
- Modify: `src/main/kotlin/com/leeturner/mtui/domain/core/ports/ProjectCreator.kt`
- Modify: `src/main/kotlin/com/leeturner/mtui/adapters/outbound/http/MicronautLaunchProjectCreator.kt`
- Modify: `src/main/kotlin/com/leeturner/mtui/domain/core/services/ProjectGenerator.kt`
- Modify: `src/main/kotlin/com/leeturner/mtui/adapters/inbound/tui/MtuiApp.kt` (call site only: pass `emptyList()` so it still compiles)
- Create: `src/test/resources/payloads/create-app-clashing-features.json`
- Test: `src/test/kotlin/com/leeturner/mtui/adapters/outbound/http/MicronautLaunchProjectCreatorTest.kt`
- Test: `src/test/kotlin/com/leeturner/mtui/domain/core/services/ProjectGeneratorTest.kt`

**Interfaces:**
- Consumes: nothing from Task 1.
- Produces:
  - `ProjectCreator.createProject(type: ApplicationType, name: ProjectName, features: List<String>): Either<GenerateProjectError, ByteArray>`
  - `ProjectGenerator.generate(type: ApplicationType, name: ProjectName, features: List<String>, into: Path): Either<GenerateProjectError, Path>`
  - `ProjectGenerator.checkAvailable(name: ProjectName, into: Path): Either<GenerateProjectError, Unit>`, which returns `ProjectAlreadyExists(into.resolve(name.folderName))` when the folder exists. `generate` keeps its own check.

- [ ] **Step 1: Record the payload**

```bash
curl -s "https://launch.micronaut.io/create/DEFAULT/com.example.demo?features=data-jdbc,data-jpa" > src/test/resources/payloads/create-app-clashing-features.json
```

- [ ] **Step 2: Write the failing tests**

Update the existing tests for the new signatures, passing `emptyList()`, then add:

```kotlin
// MicronautLaunchProjectCreatorTest
@Test
fun `selected features are sent to launch`() {
    stubCreate("com.example.my-app", aResponse().withStatus(201).withBody(byteArrayOf(1)))

    creator.createProject(type, name("com.example.my-app"), listOf("data-jdbc", "flyway"))

    expectThat(wireMock.allServeEvents.single().request.url).contains("data-jdbc").contains("flyway")
}

@Test
fun `400 for clashing features surfaces launch's message`() {
    // body: create-app-clashing-features.json
    // → ProjectRejected("There can only be one of the following features selected: [data-jdbc, data-jpa]")
}

// ProjectGeneratorTest. FakeCreator records the features it was given in `lastFeatures`.
@Test
fun `features are passed to launch`() {
    generator.generate(type, name, listOf("data-jdbc"), into)
    expectThat(creator.lastFeatures).isEqualTo(listOf("data-jdbc"))
}

@Test
fun `checkAvailable refuses an existing folder`() {
    writer.existing = true
    expectThat(generator.checkAvailable(name, into)).isLeft().get { value }.isA<ProjectAlreadyExists>()
}

@Test
fun `checkAvailable accepts a new folder`() {
    expectThat(generator.checkAvailable(name, into)).isRight()
}
```

- [ ] **Step 3: Run the tests to verify they fail**

Run: `./gradlew test --tests '*MicronautLaunchProjectCreatorTest' --tests '*ProjectGeneratorTest'`
Expected: FAIL to compile, because the new parameters and `checkAvailable` don't exist yet.

- [ ] **Step 4: Implement**

Pass `features` to `createApp` as its `features` argument. Pass `null` when the list is empty, so the request is unchanged from slice 1. In `generate`, reuse `checkAvailable(name, into).bind()` in place of the existing `ensure`.

- [ ] **Step 5: Run the tests to verify they pass**

Run: `./gradlew test`
Expected: PASS

- [ ] **Step 6: Commit**

```bash
git add src
git commit --no-gpg-sign -m "Send selected features to Launch"
```

---

### Task 3: FeaturePicker state

**Files:**
- Create: `src/main/kotlin/com/leeturner/mtui/adapters/inbound/tui/FeaturePicker.kt`
- Test: `src/test/kotlin/com/leeturner/mtui/adapters/inbound/tui/FeaturePickerTest.kt`

**Interfaces:**
- Consumes: `Feature` (Task 1).
- Produces:
  - `data class FeatureGroup(val category: String, val features: List<Feature>)`
  - `class FeaturePicker(features: List<Feature>)` with:
    - `var query: String` (setting it re-filters and fixes the highlight)
    - `val groups: List<FeatureGroup>`: the filtered groups. Categories are sorted alphabetically, features within a group by name, and empty groups are left out.
    - `val highlighted: Feature?`: the first visible feature initially, or `null` when nothing is visible
    - `val selected: List<String>`: names in the order they were selected
    - `fun isSelected(feature: Feature): Boolean`
    - `fun moveDown()`, `fun moveUp()`: move through the visible features in display order across groups, stopping at either end
    - `fun toggle()`: toggle the highlighted feature, doing nothing when it is `null`
    - `fun clearSelection()`

Keep it a plain class with no TamboUI imports.

- [ ] **Step 1: Write the failing tests**

Use a small fixture:

```kotlin
private fun feature(name: String, category: String, title: String = name, description: String = "") =
    Feature(name, title, description, category, preview = false, community = false)

private val kapt = feature("kapt", "Development Tools", title = "Kotlin annotation processing")
private val ksp = feature("ksp", "Development Tools", title = "Kotlin Symbol Processing")
private val jdbc = feature("data-jdbc", "Database", description = "Adds support for Micronaut Data JDBC")
private val postgres = feature("postgres", "Database", title = "PostgreSQL")
private val picker = FeaturePicker(listOf(postgres, ksp, jdbc, kapt))
```

Tests, as name → assertion:

- `groups are sorted by category then name`: `groups` is `[Database: data-jdbc, postgres]`, `[Development Tools: kapt, ksp]`
- `search matches name, title and description, ignoring case`: `query = "KOTLIN"` gives kapt and ksp; `"postgres"` gives postgres; `"micronaut data"` gives data-jdbc
- `categories with no matches are hidden`: `query = "kotlin"` leaves only the `Development Tools` group
- `search is trimmed and literal`: `"  ksp "` gives ksp; `"."`, `"c++"` and `"["` give empty `groups` with no exception
- `empty search shows everything`: setting `query = "ksp"` then `query = ""` gives all 4 features
- `highlight starts on the first visible feature`: `highlighted == jdbc`
- `moving crosses groups and stops at the ends`: `moveUp()` on jdbc stays on jdbc; three `moveDown()`s reach ksp; a fourth stays on ksp
- `filtering out the highlight moves it to the first visible feature`: highlight postgres, then `query = "kotlin"`, gives `highlighted == kapt`
- `highlight survives a filter that keeps it`: highlight ksp, then `query = "k"`, keeps ksp highlighted
- `nothing matching leaves nothing highlighted`: `query = "zzz"`, then `moveDown()`, `moveUp()` and `toggle()`, gives `highlighted == null` and `selected` empty
- `toggle selects and deselects in selection order`: toggle postgres, then data-jdbc, gives `selected == ["postgres", "data-jdbc"]`; toggle postgres again gives `["data-jdbc"]`
- `hidden selections stay selected and are cleared by clearSelection`: select data-jdbc, then `query = "kotlin"`, gives `selected == ["data-jdbc"]`; `clearSelection()` gives `selected` empty

- [ ] **Step 2: Run the tests to verify they fail**

Run: `./gradlew test --tests '*FeaturePickerTest'`
Expected: FAIL to compile, because `FeaturePicker` doesn't exist yet.

- [ ] **Step 3: Implement `FeaturePicker`**

Track the highlight by feature name, not by index, so that it survives re-filtering. Store the selection in a `LinkedHashSet<String>`. Use `String.contains(query.trim(), ignoreCase = true)`, which is literal, not a regex.

- [ ] **Step 4: Run the tests to verify they pass**

Run: `./gradlew test --tests '*FeaturePickerTest'`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add src
git commit --no-gpg-sign -m "Add feature picker state"
```

---

### Task 4: Picker screen and two-step flow

**Files:**
- Modify: `src/main/kotlin/com/leeturner/mtui/adapters/inbound/tui/MtuiApp.kt`
- Modify: `src/main/kotlin/com/leeturner/mtui/MtuiCommand.kt` (inject `FeatureRetriever` and pass it to `MtuiApp`)
- Modify: `docs/roadmap.md` (mark slice 2 Done in the Status column)

**Interfaces:**
- Consumes: `FeatureRetriever.getFeatures` (Task 1); `ProjectGenerator.checkAvailable` and the 4-argument `generate` (Task 2); `FeaturePicker` (Task 3).
- Produces: `MtuiApp(selectOptionRetriever, featureRetriever: FeatureRetriever, generator, workingDir)`

Behaviour the screen must have. Layout and widgets are the builder's call:

- **Loading:** fetch `/select-options`, then features for `defaultType` and `defaultLanguage`, on the same loading screen. A failure in either shows the error there, and any key exits with code 1, as in slice 1.
- **Name screen:** Enter calls `ProjectName.parse`, then `generator.checkAvailable`. Either error is shown in the form. On success, open the picker.
- **Picker screen:** a header shows the name and the defaults. The list shows category headings and rows with `[x]`/`[ ]`, the name and the title, plus `preview` and `community` tags. A "Selected (n)" pane on the right lists `picker.selected`. A line under the list shows the highlighted feature's description, and a key hint line sits at the bottom. When nothing matches, the list says so.
- **Keys on the list:** `j`/↓ calls `moveDown`, `k`/↑ calls `moveUp`, Space calls `toggle`, `x` calls `clearSelection`, and `/` focuses the search input. Enter generates with `picker.selected`. Esc clears the query if there is one, otherwise returns to the name screen. The picker instance is kept, so the selection survives.
- **Keys in the search box:** typing sets `picker.query` live. Enter or Esc returns focus to the list and keeps the query. All letters are typed into the box. None act as commands.
- **Generating:** keys are ignored, as in slice 1. Any `GenerateProjectError` is shown on the picker, which keeps the selection.
- **Ctrl+C:** quits from any screen with `MtuiOutcome.Cancelled`.
- The list scrolls so that the highlighted row stays visible.

- [ ] **Step 1: Implement the screen and wire `FeatureRetriever` into `MtuiCommand`**

Check vim keys with `event.isDown || event.isChar('j')` and so on, handled only while the list has focus. TamboUI's `BindingSets.vim()` exists, but don't use it unless you've confirmed it leaves letters alone in text inputs.

- [ ] **Step 2: Run the checks**

Run: `./gradlew check`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Manual run against real Launch**

Run `./gradlew installDist`, then `cd $(mktemp -d)` and run `<repo>/build/install/mtui/bin/mtui`. Check each of these:

1. Loading, then the name screen. Enter `com.example.demo` to open the picker, which shows about 318 features under category headings.
2. `j`/`k` and the arrows move, skipping headings and stopping at both ends.
3. `/` then `jdbc`: the list narrows live and the letters land in the box, not as commands. Enter returns to the list with the filter kept.
4. Space on `data-jdbc` and `flyway`: both appear in "Selected (2)", in that order. Clear the filter: both stay selected.
5. `x` clears the selection.
6. Select `data-jdbc` and `data-jpa`, then Enter: Launch's "There can only be one of…" message shows on the picker, with the selection kept.
7. Esc (no filter) returns to the name screen. Change the name to `MyApp`, Enter, then Enter again: "Invalid package name: MyApp" shows on the picker. Esc, then fix the name.
8. With `data-jdbc` and `postgres` selected, Enter: `./demo/` is created, and its `build.gradle.kts` contains `micronaut-data-jdbc` and `postgresql`.
9. Ctrl+C on the picker exits with code 1 and restores the terminal.

- [ ] **Step 4: Mark slice 2 Done in `docs/roadmap.md` and commit**

```bash
git add src docs/roadmap.md
git commit --no-gpg-sign -m "Add the feature picker screen"
```

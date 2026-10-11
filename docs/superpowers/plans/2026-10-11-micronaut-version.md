# Micronaut Version Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** On the name screen, the user chooses between the release (`launch.micronaut.io`) and the snapshot (`snapshot.micronaut.io`) Launch server. Each is labelled with its Micronaut version from `/versions`, and every Launch call goes to the chosen server.

**Architecture:** The domain gets a `LaunchServer` enum and a `MicronautVersion` (server plus label). The chosen version becomes the first field of `ProjectOptions`. The ports take the server, and `ProjectCreator` reads it from the options. In the HTTP adapters, each generated-client call is wrapped in `onServer(server) { … }`, which puts the server into Micronaut's `PropagatedContext`. A `@ClientFilter` on the `micronaut-launch` service then rewrites the request to the snapshot URL when the snapshot is chosen. This keeps the single generated client. `OptionsForm` gets a Version row, and `MtuiApp` reloads the options when it changes.

**Tech Stack:** Kotlin 2.4, Micronaut 5.2 + Picocli, generated Micronaut OpenAPI client, Arrow `Either`, TamboUI 0.5.0 toolkit, JUnit 5, Strikt, WireMock (`io.github.leeturner:wiremock-micronaut`).

**Spec:** `docs/superpowers/specs/2026-10-11-micronaut-version-design.md`

## Global Constraints

- Two servers only: release `https://launch.micronaut.io` and snapshot `https://snapshot.micronaut.io`. No `prev`, and no custom URL.
- The label is the `micronaut.version` entry of the server's `/versions`. If it is missing or the call fails, the label is "Release" or "Snapshot".
- The release is chosen at start-up, and release comes first on the row. ←/→ and `h`/`l` stop at the ends. They don't wrap.
- When switching, each choice (type, language, build, test, JDK) is kept if the new server offers the same `value`. Otherwise it falls back to the new server's default. Language defaults are **not** applied.
- A failed switch shows Launch's error on the name screen. The version, options and name stay as they were.
- The domain and the TUI never see a URL or a client. They only know a `LaunchServer`.
- Errors are typed with Arrow `Either`. Never throw across a port.
- No new dependencies. Nothing is remembered between runs.
- Gradle runs on Java 25 (`.sdkmanrc`). On Java 26, kapt fails with a javaparser `NoSuchMethodError`, so use `JAVA_HOME=~/.sdkman/candidates/java/25-tem` if needed. `./gradlew check` (tests + kotlinter + detekt) passes after every task. Commit with `git commit --no-gpg-sign`.

## Review Focus

1. **Release requests are never rewritten:** with `RELEASE` chosen, a request goes to the configured release URL, and the snapshot WireMock gets nothing. (Task 2 test.)
2. **Keys while switching:** pressing →/← again, Enter, or typing while the snapshot's options load must not start a second load. Ctrl+C still quits. (Task 5 manual check.)
3. **A switch to an unreachable snapshot:** the error shows, the mark stays on the release, and pressing → again retries. (Task 5 manual check, with `MTUI_LAUNCH_SNAPSHOT_URL=http://localhost:1`.)
4. **Same type and language, different server:** go back from the picker, switch version, then press Enter. Features must be fetched again, because `featuresFor` includes the server. (Task 5 manual check.)
5. **The snapshot's `/versions` is down at start-up:** the form still opens promptly, and the row shows "Snapshot". (Task 3 test for the label; Task 5 manual check with the same unreachable URL.)

---

### Task 1: Domain: Launch server, Micronaut version, carrying choices over

**Files:**
- Create: `src/main/kotlin/com/leeturner/mtui/domain/core/model/MicronautVersion.kt`
- Modify: `src/main/kotlin/com/leeturner/mtui/domain/core/model/ProjectOptions.kt`
- Modify: `src/main/kotlin/com/leeturner/mtui/adapters/inbound/tui/OptionsForm.kt` (constructor only)
- Modify: `src/main/kotlin/com/leeturner/mtui/adapters/inbound/tui/OptionsFormScreen.kt` (constructor only)
- Modify: `src/main/kotlin/com/leeturner/mtui/adapters/inbound/tui/MtuiApp.kt` (one call site)
- Test: `src/test/kotlin/com/leeturner/mtui/domain/core/model/ProjectOptionsTest.kt`, plus the `ProjectOptions(...)`/`OptionsForm(...)` fixtures in `ProjectGeneratorTest`, `MicronautLaunchProjectCreatorTest` and `OptionsFormTest`

**Interfaces:**
- Produces:
  - `enum class LaunchServer(val fallbackLabel: String) { RELEASE("Release"), SNAPSHOT("Snapshot") }`
  - `data class MicronautVersion(val server: LaunchServer, val label: String)` with `companion object { fun fallback(server: LaunchServer): MicronautVersion }`, which uses `server.fallbackLabel`.
  - `ProjectOptions` gets `val version: MicronautVersion` as its **first** property.
  - `ProjectOptions.defaultsFrom(options: SelectOptions, version: MicronautVersion): ProjectOptions`
  - `fun ProjectOptions.onServer(version: MicronautVersion, options: SelectOptions): ProjectOptions` lives in `ProjectOptions.kt`, next to `withLanguage`. For each of type, language, build, test and JDK, it uses the `options` entry with the same `value`, and that server's default otherwise. The kept entries are the **new** options' objects, so `==` against the new lists works.
  - `OptionsForm(options: SelectOptions, versions: List<MicronautVersion>)`, with `chosen` starting at `defaultsFrom(options, versions.first())`. `OptionsFormScreen(options: SelectOptions, versions: List<MicronautVersion>)` passes both through.
  - Until Task 5, `MtuiApp` passes `LaunchServer.entries.map(MicronautVersion::fallback)`.

- [ ] **Step 1: Write the failing tests in `ProjectOptionsTest`**

Add a second options fixture, `snapshotOptions`. It has the same values as `options`, except that the Kotlin language has a different label ("Kotlin (snapshot)") and defaults `JUNIT`/`GRADLE_KOTLIN`, and Maven is missing. Its defaults are Java, Gradle Kotlin and JUnit.

```kotlin
@Test fun `defaults start on the given version`()
// ProjectOptions.defaultsFrom(options, release).version == release

@Test fun `switching server keeps choices the new server offers, as its own objects`()
// kotlinGradleKotest = defaults.copy(language = kotlin, build = gradle, test = kotest)
// result = kotlinGradleKotest.onServer(snapshot, snapshotOptions)
// result.version == snapshot; result.language.label == "Kotlin (snapshot)"; result.test.value == "KOTEST"; result.build.value == "GRADLE"

@Test fun `switching server falls back to its default for a choice it doesn't offer`()
// defaults.copy(build = maven).onServer(snapshot, snapshotOptions).build == snapshotOptions.defaultBuildType

@Test fun `switching server doesn't apply language defaults`()
// defaults.copy(language = kotlin, test = kotest).onServer(snapshot, snapshotOptions).test.value == "KOTEST"
```

Here `release = MicronautVersion(LaunchServer.RELEASE, "5.2.2")` and `snapshot = MicronautVersion(LaunchServer.SNAPSHOT, "5.2.3-SNAPSHOT")`. Update the existing `defaultsFrom` test to pass `release`.

- [ ] **Step 2: Run the tests and check they fail**

Run: `./gradlew test --tests '*ProjectOptionsTest'`
Expected: FAIL to compile, because `MicronautVersion` doesn't exist yet.

- [ ] **Step 3: Implement `MicronautVersion.kt`, the `version` property, `defaultsFrom` and `onServer`. Update the constructor call sites and test fixtures listed above so everything compiles.**

- [ ] **Step 4: Run the checks and check they pass**

Run: `./gradlew check`
Expected: BUILD SUCCESSFUL

- [ ] **Step 5: Commit**

```bash
git add -A src
git commit --no-gpg-sign -m "Add the Micronaut version to the chosen options"
```

---

### Task 2: Route Launch calls to the chosen server

This is the riskiest task. Its tests decide whether the `PropagatedContext` and client-filter approach works. If the filter can't see the element, or can't rewrite the request so that it reaches the snapshot WireMock, **stop and report back**. Don't work around it. The fallback is a second `client(...)` block in `build.gradle.kts` with its own `clientId`, and that changes the plan.

**Files:**
- Create: `src/main/kotlin/com/leeturner/mtui/adapters/outbound/http/LaunchServerRouting.kt`
- Modify: `src/main/resources/application.properties`
- Modify: ports `SelectOptionRetriever.kt`, `FeatureRetriever.kt`; adapters `MicronautLaunchSelectOptionRetriever.kt`, `MicronautLaunchFeatureRetriever.kt`, `MicronautLaunchProjectCreator.kt`; `CatalogLoader.kt`; `MtuiApp.kt` (call sites only)
- Test: `MicronautLaunchSelectOptionRetrieverTest.kt`, `MicronautLaunchFeatureRetrieverTest.kt`, `MicronautLaunchProjectCreatorTest.kt`, `CatalogLoaderTest.kt`

**Interfaces:**
- Consumes: `LaunchServer`, and `ProjectOptions.version` (Task 1).
- Produces:
  - `SelectOptionRetriever.getSelectOptions(server: LaunchServer)`
  - `FeatureRetriever.getFeatures(server: LaunchServer, type: ApplicationType, language: Language)`
  - `CatalogLoader.loadOptions(server: LaunchServer)`
  - `CatalogLoader.loadFeatures(server: LaunchServer, type: ApplicationType, language: Language)`
  - `ProjectCreator.createProject` is unchanged. The adapter uses `options.version.server`.
  - In `LaunchServerRouting.kt` (internal to the http adapter package):
    - `data class ChosenLaunchServer(val server: LaunchServer) : PropagatedContextElement`
    - `fun <T> onServer(server: LaunchServer, call: () -> T): T`, which runs `call` inside `PropagatedContext.getOrEmpty().plus(ChosenLaunchServer(server)).propagate().use { … }`.
    - `@ClientFilter(serviceId = ["micronaut-launch"]) class LaunchServerFilter(@Value("\${mtui.launch.snapshot-url}") snapshotUrl: URI)`, with a `@RequestFilter` method taking `MutableHttpRequest<*>`. When `PropagatedContext.find(ChosenLaunchServer::class.java)` is `SNAPSHOT`, it swaps the request URI's scheme, host and port for `snapshotUrl`'s and keeps the path and query. Otherwise it leaves the request alone. (If the URI is still relative when the filter runs, make it absolute against `snapshotUrl` instead.)
  - `application.properties`: `mtui.launch.snapshot-url=https://snapshot.micronaut.io`

- [ ] **Step 1: Write the failing tests**

In each of the three adapter test classes, add a second WireMock to `@EnableWireMock`: `ConfigureWireMock(name = "snapshot", baseUrlProperties = ["mtui.launch.snapshot-url"])`. Inject it as `snapshot`. Pass `LaunchServer.RELEASE` in the existing calls, and give the creator test's `options` fixture a release version.

```kotlin
// MicronautLaunchSelectOptionRetrieverTest
@Test fun `snapshot server is called when it is chosen`()
// stub /select-options on `snapshot` with get-select-options.json; getSelectOptions(SNAPSHOT) isRight; wireMock.allServeEvents isEmpty()
@Test fun `release server is called when it is chosen`()
// stub on `wireMock`; getSelectOptions(RELEASE) isRight; snapshot.allServeEvents isEmpty()

// MicronautLaunchFeatureRetrieverTest
@Test fun `snapshot server is called when it is chosen`()
// stub /application-types/DEFAULT/features/JAVA on `snapshot`; getFeatures(SNAPSHOT, type, language) isRight; wireMock.allServeEvents isEmpty()

// MicronautLaunchProjectCreatorTest
@Test fun `snapshot server is called when it is chosen`()
// stub create on `snapshot` (201 + zip bytes); createProject(options.copy(version = snapshot), …) isRight; wireMock.allServeEvents isEmpty()
```

In `CatalogLoaderTest`, the fakes record the server they were called with. Assert that `loadOptions(SNAPSHOT)` and `loadFeatures(SNAPSHOT, cli, kotlin)` pass `SNAPSHOT` through.

- [ ] **Step 2: Run the tests and check they fail**

Run: `./gradlew test`
Expected: FAIL to compile, because the ports don't take a server yet.

- [ ] **Step 3: Change the ports, `CatalogLoader` and the adapters.** Each adapter wraps its generated-client call in `onServer(…) { }`. Add `LaunchServerRouting.kt` and the property. In `MtuiApp`, the start-up load passes `LaunchServer.RELEASE`, and the features fetch passes `chosen.version.server`.

- [ ] **Step 4: Run the checks and check they pass**

Run: `./gradlew check`
Expected: BUILD SUCCESSFUL

- [ ] **Step 5: Commit**

```bash
git add -A src
git commit --no-gpg-sign -m "Send Launch calls to the chosen server"
```

---

### Task 3: Version labels from `/versions`

**Files:**
- Create: `src/main/kotlin/com/leeturner/mtui/domain/core/ports/VersionRetriever.kt`
- Create: `src/main/kotlin/com/leeturner/mtui/adapters/outbound/http/MicronautLaunchVersionRetriever.kt`
- Modify: `src/main/kotlin/com/leeturner/mtui/domain/core/model/MicronautVersion.kt` (add the error)
- Modify: `src/main/kotlin/com/leeturner/mtui/domain/core/services/CatalogLoader.kt`
- Create: `src/test/resources/payloads/get-versions.json`, `src/test/resources/payloads/get-versions-missing-micronaut.json`
- Test: `src/test/kotlin/com/leeturner/mtui/adapters/outbound/http/MicronautLaunchVersionRetrieverTest.kt`, `CatalogLoaderTest.kt`

**Interfaces:**
- Consumes: `LaunchServer`, `MicronautVersion`, `onServer` (Tasks 1 and 2).
- Produces:
  - `data class VersionError(val status: Int?, val message: String)`
  - `interface VersionRetriever { fun getMicronautVersion(server: LaunchServer): Either<VersionError, String> }`
  - `MicronautLaunchVersionRetriever` uses the generated `getInfo()` and reads `versions["micronaut.version"]`. If the entry is missing, it returns `VersionError(null, "No micronaut.version in /versions")`. An `HttpClientException` becomes `VersionError(status, message)`, the same as in `MicronautLaunchFeatureRetriever`.
  - `CatalogLoader(selectOptionRetriever, featureRetriever, versionRetriever)`
  - `CatalogLoader.loadVersions(): List<MicronautVersion>` returns one entry per `LaunchServer.entries`, in that order. On a `Left`, that entry is `MicronautVersion.fallback(server)`. It never fails.

- [ ] **Step 1: Record the payloads**

```bash
curl -s https://launch.micronaut.io/versions > src/test/resources/payloads/get-versions.json
echo '{"versions":{"groovy.version":"5.0.8"}}' > src/test/resources/payloads/get-versions-missing-micronaut.json
```

Note the recorded `micronaut.version` (5.2.2 when this plan was written). The test below asserts it.

- [ ] **Step 2: Write the failing tests**

```kotlin
// MicronautLaunchVersionRetrieverTest — same two-WireMock setup as Task 2, stubbing GET /versions
@Test fun `200 returns the micronaut version`()            // getMicronautVersion(RELEASE) isRight == recorded value
@Test fun `missing micronaut version is an error`()        // isLeft == VersionError(null, "No micronaut.version in /versions")
@Test fun `500 is an error with its status`()              // isLeft, status == 500
@Test fun `snapshot server is called when it is chosen`()  // stub on snapshot; isRight; wireMock.allServeEvents isEmpty()

// CatalogLoaderTest — FakeVersionRetriever returning per-server results
@Test fun `versions are labelled from each server`()
// RELEASE → "5.2.2", SNAPSHOT → "5.2.3-SNAPSHOT"
// loadVersions() == listOf(MicronautVersion(RELEASE, "5.2.2"), MicronautVersion(SNAPSHOT, "5.2.3-SNAPSHOT"))
@Test fun `a server whose version can't be read gets its fallback label`()
// SNAPSHOT → Left → loadVersions()[1] == MicronautVersion(SNAPSHOT, "Snapshot")
```

- [ ] **Step 3: Run the tests and check they fail**

Run: `./gradlew test --tests '*VersionRetrieverTest' --tests '*CatalogLoaderTest'`
Expected: FAIL to compile, because `VersionRetriever` doesn't exist yet.

- [ ] **Step 4: Implement the port, the adapter and `loadVersions`**

- [ ] **Step 5: Run the checks and check they pass**

Run: `./gradlew check`
Expected: BUILD SUCCESSFUL

- [ ] **Step 6: Commit**

```bash
git add -A src
git commit --no-gpg-sign -m "Label each Launch server with its Micronaut version"
```

---

### Task 4: Version row in the options form state

**Files:**
- Modify: `src/main/kotlin/com/leeturner/mtui/adapters/inbound/tui/OptionsForm.kt`
- Test: `src/test/kotlin/com/leeturner/mtui/adapters/inbound/tui/OptionsFormTest.kt`

**Interfaces:**
- Consumes: `MicronautVersion`, `ProjectOptions.onServer` (Task 1).
- Produces:
  - `OptionRow.VERSION("Version")` as the **first** entry.
  - `choices(OptionRow.VERSION)` returns one `Choice(label, chosen)` per version.
  - `fun neighbourVersion(by: Int): MicronautVersion?` returns the version `by` steps from the chosen one, or `null` past either end.
  - `fun switchTo(version: MicronautVersion, options: SelectOptions)` replaces the form's options (`options` becomes a `private var`) and sets `chosen = chosen.onServer(version, options)`.
  - `next`/`previous` on `VERSION` leave `chosen` unchanged. Switching needs the other server's options, so it goes through `switchTo`.

- [ ] **Step 1: Write the failing tests**

The fixture has `versions = listOf(release, snapshot)`, using the labels "5.2.2" and "5.2.3-SNAPSHOT".

```kotlin
@Test fun `version row comes first and starts on the first version`()
// OptionRow.entries.first() == VERSION
// choices(VERSION) == listOf(Choice("5.2.2", true), Choice("5.2.3-SNAPSHOT", false))

@Test fun `neighbour version stops at the ends`()
// neighbourVersion(1) == snapshot; neighbourVersion(-1) == null
// after switchTo(snapshot, options): neighbourVersion(1) == null; neighbourVersion(-1) == release

@Test fun `next on the version row doesn't switch`()
// form.next(VERSION); chosen.version == release

@Test fun `switching keeps the choices and marks the new version`()
// repeat(2) { next(LANGUAGE) }  → Kotlin
// switchTo(snapshot, options); chosen.language.value == "KOTLIN"; choices(VERSION) marks "5.2.3-SNAPSHOT"
```

- [ ] **Step 2: Run the tests and check they fail**

Run: `./gradlew test --tests '*OptionsFormTest'`
Expected: FAIL to compile, because `OptionRow.VERSION` doesn't exist yet.

- [ ] **Step 3: Implement the row, `neighbourVersion` and `switchTo`**

- [ ] **Step 4: Run the checks and check they pass**

Run: `./gradlew check`
Expected: BUILD SUCCESSFUL. The screen will show the Version row, but ←/→ on it does nothing yet.

- [ ] **Step 5: Commit**

```bash
git add -A src
git commit --no-gpg-sign -m "Add the version row to the options form"
```

---

### Task 5: Switch version on the name screen

**Files:**
- Modify: `src/main/kotlin/com/leeturner/mtui/adapters/inbound/tui/OptionsFormScreen.kt`
- Modify: `src/main/kotlin/com/leeturner/mtui/adapters/inbound/tui/MtuiApp.kt`
- Modify: `src/main/kotlin/com/leeturner/mtui/adapters/inbound/tui/FeaturePickerScreen.kt` (`summary()`)

**Interfaces:**
- Consumes: `CatalogLoader.loadVersions()`, `loadOptions(server)`, `loadFeatures(server, …)`, `OptionsForm.neighbourVersion`/`switchTo`.
- Produces (TUI-internal):
  - `FormAction` becomes `sealed interface FormAction { data object None; data object Submit; data object Quit; data class SwitchVersion(val version: MicronautVersion) }`. On the Version row, ←/`h` and →/`l` return `SwitchVersion(neighbourVersion(∓1))` when it isn't null, and `None` otherwise.
  - `Screen.Form` gets `switchingTo: MicronautVersion? = null`. The form counts as busy when `fetching || switchingTo != null`. A busy form ignores every key except Ctrl+C, and `submitName` ignores Enter.
  - ↓ from the name box focuses `OptionRow.entries.first()` (now Version) instead of the hard-coded `OptionRow.TYPE`.
  - `OptionsFormScreen.render(error, fetching, switchingTo, focus, onSubmit)`. While switching, the spinner slot under the name box shows `Loading ${switchingTo.label} options…`, and the option rows are dimmed.

Behaviour, in `MtuiApp`:
- **Start-up:** start `catalogLoader.loadVersions()` with `CompletableFuture.supplyAsync`, then load the release options. After the options arrive, wait for the versions with `completeOnTimeout(LaunchServer.entries.map(MicronautVersion::fallback), 2, SECONDS)`, then build `OptionsFormScreen(options, versions)`. Add the comment `// ponytail: one 2s timeout for both labels; a slow release /versions also loses the snapshot's label`.
- **Switching:** set `screen = Screen.Form(switchingTo = version)` without moving focus. In `background`, call `loadOptions(version.server)`. On `Right`, call `form.switchTo(version, options)` and then set `Screen.Form()`. On `Left`, set `Screen.Form(error = message)`. The version and options are untouched, and focus stays on the Version row.
- `featuresFor` becomes `Triple(chosen.version.server, chosen.type, chosen.language)`.
- `summary()` puts `version.label` first: `5.2.3-SNAPSHOT · DEFAULT · Kotlin · …`.

- [ ] **Step 1: Implement the above**

- [ ] **Step 2: Run the checks**

Run: `./gradlew check`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Manual run against real Launch** (`./gradlew installDist`, then `build/install/mtui/bin/mtui` in a scratch directory)

1. The Version row shows `5.2.2` and `5.2.3-SNAPSHOT` (or the current values), with the release marked.
2. Choose Kotlin, focus Version, and press →. The spinner shows "Loading 5.2.3-SNAPSHOT options…". Press → and Enter a few times during the load: there is only one load. Afterwards, Kotlin is still chosen and the snapshot is marked.
3. Type a name, press Enter, and check the header starts with `5.2.3-SNAPSHOT`. Pick a feature and generate. Check that the project's `gradle.properties` has `micronautVersion=5.2.3-SNAPSHOT`.
4. Run it again. Generate on the release, go back with Esc, switch to the snapshot, and press Enter. Check that "Fetching features…" shows, so features were fetched again.
5. Run with `MTUI_LAUNCH_SNAPSHOT_URL=http://localhost:1`. The form opens within about 2 seconds, with "Snapshot" on the row. Pressing → shows the error, the release stays marked, and pressing → again retries.
6. Press Ctrl+C during a switch to quit.

- [ ] **Step 4: Commit**

```bash
git add -A src
git commit --no-gpg-sign -m "Switch the Micronaut version on the name screen"
```

# Walking Skeleton Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** `mtui` loads the Launch defaults, asks for one `com.example.my-app` style name, generates the project via Launch and unzips it into `./my-app/`.

**Architecture:** Hexagonal, following the existing `SelectOptionRetriever` pattern. Two new outbound ports, `ProjectCreator` (HTTP) and `ProjectWriter` (filesystem), are combined by a `ProjectGenerator` domain service. The TamboUI app is the inbound side. `MtuiCommand` runs it and turns the outcome into an exit code and the final printed lines.

**Tech Stack:** Kotlin 2.4, Micronaut 5.2 + Picocli, generated Micronaut OpenAPI client, Arrow `Either`, TamboUI 0.5.0 toolkit, JUnit 5, Strikt, WireMock (`io.github.leeturner:wiremock-micronaut`).

**Spec:** `docs/superpowers/specs/2026-10-10-walking-skeleton-design.md`

## Global Constraints

- Errors are typed with Arrow `Either` and sealed error types. Never throw across a port.
- Never overwrite or merge into an existing folder.
- Name characters allowed: `[A-Za-z0-9._-]`. Anything else is rejected before calling Launch.
- Exit codes: `0` = project created; `1` = cancelled (Esc / Ctrl+C) or Launch unreachable; `2` = Picocli usage error (already the case).
- After success, print exactly two lines to stdout: the absolute project path, then `cd <folderName>`.
- No new CLI options. Remove `--verbose` and give the command a real description.
- No new dependencies. Zip handling uses `java.util.zip`.
- `./gradlew check` (tests + kotlinter + detekt) passes after every task. Commit with `git commit --no-gpg-sign`.

## Review Focus

1. **Sloppy names:** pasted `" com.example.my-app\n"` should be trimmed and work. `com.example.`, `.my-app` and `com..app` should be rejected in the form, not turned into an empty folder name. (Task 1 tests.)
2. **A 400 whose body isn't Launch's usual JSON** (empty, HTML from a proxy) should still produce a readable error and not crash. (Task 2 test.)
3. **Folder appears between the pre-check and the write**, or the zip's top folder already exists: refuse and leave the existing folder untouched. (Task 3 test.)
4. **Write fails halfway:** delete the folder we created. Leave nothing half-written. (Task 3 test.)
5. **Enter pressed twice, or Esc pressed while generating:** there must be only one `/create` call, and no half-extracted folder from a quit mid-write. (Task 5 manual check: Enter is ignored while generating and Esc is ignored until the write finishes.)

---

### Task 1: ProjectName

**Files:**
- Create: `src/main/kotlin/com/leeturner/mtui/domain/core/model/ProjectName.kt`
- Test: `src/test/kotlin/com/leeturner/mtui/domain/core/model/ProjectNameTest.kt`

**Interfaces:**
- Produces:
  - `class ProjectName private constructor(val value: String)` with `val folderName: String` (the part after the last `.`, or the whole value if there is no `.`) and `companion object { fun parse(raw: String): Either<InvalidProjectName, ProjectName> }`
  - `data class InvalidProjectName(val message: String)`

- [ ] **Step 1: Write the failing tests**

```kotlin
class ProjectNameTest {
    @Test
    fun `last segment is the folder name`() {
        expectThat(ProjectName.parse("com.example.my-app")).isRight().and {
            get { value.value }.isEqualTo("com.example.my-app")
            get { value.folderName }.isEqualTo("my-app")
        }
    }

    @Test
    fun `a name without a package is its own folder name`() {
        expectThat(ProjectName.parse("my-app")).isRight().get { value.folderName }.isEqualTo("my-app")
    }

    @Test
    fun `surrounding whitespace is trimmed`() {
        expectThat(ProjectName.parse(" com.example.my-app\n")).isRight().get { value.value }.isEqualTo("com.example.my-app")
    }

    @Test
    fun `blank name is rejected`() {
        expectThat(ProjectName.parse("  ")).isLeft().get { value.message }.isEqualTo("Name required")
    }

    @Test
    fun `characters launch cannot route are rejected`() {
        expect {
            listOf("My App", "my-app!", "com/example/app", "café").forEach { raw ->
                that(ProjectName.parse(raw)).describedAs(raw).isLeft().get { value.message }
                    .isEqualTo("Only letters, digits, '.', '-' and '_' are allowed")
            }
        }
    }

    @Test
    fun `empty segments are rejected`() {
        expect {
            listOf("com.example.", ".my-app", "com..app").forEach { raw ->
                that(ProjectName.parse(raw)).describedAs(raw).isLeft().get { value.message }
                    .isEqualTo("Name can't start or end with '.' or contain '..'")
            }
        }
    }
}
```

`junit-jupiter-params` is not on the classpath, so these tests use Strikt's `expect { }` soft-assertion block instead.

- [ ] **Step 2: Run to verify it fails**

Run: `./gradlew test --tests '*ProjectNameTest'`
Expected: compilation failure, `ProjectName` unresolved.

- [ ] **Step 3: Implement `ProjectName.parse`** in `ProjectName.kt`. Trim, then check in order: blank, character set, empty segments. Use the exact messages from the tests.

- [ ] **Step 4: Run to verify it passes**

Run: `./gradlew test --tests '*ProjectNameTest'`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add src/main/kotlin/com/leeturner/mtui/domain/core/model/ProjectName.kt src/test/kotlin/com/leeturner/mtui/domain/core/model/ProjectNameTest.kt
git commit --no-gpg-sign -m "Add ProjectName parsing"
```

---

### Task 2: ProjectCreator port and Launch adapter

**Files:**
- Modify: `src/main/openapi/micronaut-launch-5.2.2.yml:152` (the `/create/{type}/{name}` response)
- Create: `src/main/kotlin/com/leeturner/mtui/domain/core/model/GenerateProjectError.kt`
- Create: `src/main/kotlin/com/leeturner/mtui/domain/core/ports/ProjectCreator.kt`
- Create: `src/main/kotlin/com/leeturner/mtui/adapters/outbound/http/MicronautLaunchProjectCreator.kt`
- Create: `src/test/resources/payloads/create-app-invalid-name.json`
- Test: `src/test/kotlin/com/leeturner/mtui/adapters/outbound/http/MicronautLaunchProjectCreatorTest.kt`

**Interfaces:**
- Consumes: `ProjectName` (Task 1); the existing `ApplicationType` (`value` is e.g. `"DEFAULT"`).
- Produces:
  - `sealed interface GenerateProjectError { val message: String }` with:
    - `data class ProjectAlreadyExists(val path: Path)`, with message `"$path already exists"`
    - `data class ProjectRejected(override val message: String)`, for a Launch 400
    - `data class UnexpectedProjectCreationError(val status: Int?, override val message: String)`
    - `data class ProjectWriteFailed(override val message: String)`
  - `interface ProjectCreator { fun createProject(type: ApplicationType, name: ProjectName): Either<GenerateProjectError, ByteArray> }`
  - `@Singleton class MicronautLaunchProjectCreator(api: MicronautLaunchDefaultApi) : ProjectCreator`

- [ ] **Step 1: Make the generated client return the zip.** Replace line 152, `application/zip: {}`, under `/create/{type}/{name}` (not the one under `/{name}.zip`) with:

```yaml
            # mtui patch: no schema upstream, so the generated createApp returned Unit
            application/zip:
              schema:
                type: string
                format: binary
```

Run: `./gradlew generateClientOpenApiApis && grep -A10 'fun createApp' build/generated/openapi/generateClientOpenApiApis/src/main/kotlin/com/leeturner/mtui/adapters/outbound/http/client/api/MicronautLaunchDefaultApi.kt`
Expected: the return type is `ByteBuffer<*>`. This was verified against the real API on 2026-10-10: the bytes start with `PK`.

- [ ] **Step 2: Record the 400 payload** into `create-app-invalid-name.json`. This is the real Launch response:

```json
{"_links":{"self":[{"href":"/create/DEFAULT/MyApp","templated":false}]},"_embedded":{"errors":[{"message":"Invalid package name: MyApp"}]},"message":"Bad Request"}
```

- [ ] **Step 3: Write the failing tests.** Use the same `@MicronautTest` / `@EnableWireMock` setup as `MicronautLaunchSelectOptionRetrieverTest`. Stub `GET /create/DEFAULT/<name>`. `type` is `ApplicationType(title = "", name = "", description = "", value = "DEFAULT", label = "")`.

```kotlin
@Test
fun `201 returns the zip bytes`() {
    val zip = byteArrayOf(0x50, 0x4B, 0x03, 0x04)
    // stub: status 201, Content-Type application/zip, body zip
    expectThat(creator.createProject(type, name("com.example.my-app"))).isRight().get { value.toList() }.isEqualTo(zip.toList())
}

@Test
fun `400 surfaces launch's message`() {
    // stub: status 400, Content-Type application/json, body from create-app-invalid-name.json
    expectThat(creator.createProject(type, name("MyApp"))).isLeft().isA<ProjectRejected>()
        .get { message }.isEqualTo("Invalid package name: MyApp")
}

@Test
fun `400 with an unreadable body falls back to the status text`() {
    // stub: status 400, Content-Type text/html, body "<html>nope</html>"
    expectThat(creator.createProject(type, name("MyApp"))).isLeft().isA<ProjectRejected>()
        .get { message }.isEqualTo("Bad Request")
}

@Test
fun `connection failure is an unexpected error`() {
    // stub: Fault.CONNECTION_RESET_BY_PEER
    expectThat(creator.createProject(type, name("com.example.my-app"))).isLeft().isA<UnexpectedProjectCreationError>()
        .get { status }.isNull()
}

@Test
fun `500 is an unexpected error with its status`() {
    // stub: status 500
    expectThat(creator.createProject(type, name("com.example.my-app"))).isLeft().isA<UnexpectedProjectCreationError>()
        .get { status }.isEqualTo(500)
}
```

`name(raw)` is a private test helper: `ProjectName.parse(raw).getOrNull()!!`. Note that `MyApp` passes `ProjectName`; only Launch rejects it.

- [ ] **Step 4: Run to verify it fails**

Run: `./gradlew test --tests '*MicronautLaunchProjectCreatorTest'`
Expected: compilation failure, `MicronautLaunchProjectCreator` unresolved.

- [ ] **Step 5: Implement the adapter.** Call `api.createApp(MicronautLaunchApplicationType.fromValue(type.value), name.value)` and return `.toByteArray()`. Error handling mirrors `MicronautLaunchSelectOptionRetriever`:
  - `HttpClientResponseException` with status 400 → `ProjectRejected`. The message is read with `e.response.getBody(LaunchErrorBody::class.java)` → `_embedded.errors[0].message`. Fall back to `e.message ?: "Bad Request"`.
  - any other `HttpClientException` → `UnexpectedProjectCreationError(status, message)`.

  `LaunchErrorBody` is a private `@Serdeable` data class in the same file, with `@JsonProperty("_embedded")`. Don't use Micronaut's `JsonError`: it was tried during the spike, and it loses the embedded message.

- [ ] **Step 6: Run to verify it passes**

Run: `./gradlew test --tests '*MicronautLaunchProjectCreatorTest'`
Expected: PASS

- [ ] **Step 7: Commit**

```bash
git add src/main/openapi src/main/kotlin/com/leeturner/mtui/domain src/main/kotlin/com/leeturner/mtui/adapters/outbound/http src/test
git commit --no-gpg-sign -m "Create projects through Micronaut Launch"
```

---

### Task 3: ProjectWriter port and zip adapter

**Files:**
- Create: `src/main/kotlin/com/leeturner/mtui/domain/core/ports/ProjectWriter.kt`
- Create: `src/main/kotlin/com/leeturner/mtui/adapters/outbound/filesystem/ZipProjectWriter.kt`
- Test: `src/test/kotlin/com/leeturner/mtui/adapters/outbound/filesystem/ZipProjectWriterTest.kt`

**Interfaces:**
- Consumes: `ProjectName`, `GenerateProjectError` and its subtypes (Tasks 1–2).
- Produces:
  - `interface ProjectWriter { fun exists(name: ProjectName, into: Path): Boolean; fun write(zip: ByteArray, into: Path): Either<GenerateProjectError, Path> }`. `write` returns the absolute path of the created project folder.
  - `@Singleton class ZipProjectWriter : ProjectWriter`

- [ ] **Step 1: Write the failing tests.** These are plain JUnit tests (no `@MicronautTest`) using `@TempDir into: Path`. The private helper `zipOf(vararg entries: Pair<String, String>): ByteArray` builds a zip with `ZipOutputStream`. An entry whose name ends in `/` is a directory.

```kotlin
@Test
fun `extracts into a new top-level folder`() {
    val zip = zipOf("my-app/" to "", "my-app/build.gradle.kts" to "plugins {}", "my-app/src/main/App.kt" to "fun main() {}")
    expectThat(writer.write(zip, into)).isRight().get { value }.isEqualTo(into.resolve("my-app").toAbsolutePath())
    expectThat(into.resolve("my-app/src/main/App.kt").readText()).isEqualTo("fun main() {}")
}

@Test
fun `wrapper scripts are executable`() {
    val zip = zipOf("my-app/gradlew" to "#!/bin/sh", "my-app/mvnw" to "#!/bin/sh", "my-app/README.md" to "")
    writer.write(zip, into)
    expectThat(Files.isExecutable(into.resolve("my-app/gradlew"))).isTrue()
    expectThat(Files.isExecutable(into.resolve("my-app/mvnw"))).isTrue()
    expectThat(Files.isExecutable(into.resolve("my-app/README.md"))).isFalse()
}

@Test
fun `an entry escaping the target rejects the zip and writes nothing`() {
    val zip = zipOf("my-app/ok.txt" to "", "my-app/../../evil.txt" to "")
    expectThat(writer.write(zip, into)).isLeft().isA<ProjectWriteFailed>()
    expectThat(into.listDirectoryEntries()).isEmpty()
}

@Test
fun `more than one top-level folder rejects the zip and writes nothing`() {
    val zip = zipOf("my-app/a.txt" to "", "other/b.txt" to "")
    expectThat(writer.write(zip, into)).isLeft().isA<ProjectWriteFailed>()
    expectThat(into.listDirectoryEntries()).isEmpty()
}

@Test
fun `an existing folder is refused and left untouched`() {
    into.resolve("my-app").createDirectory().resolve("keep.txt").writeText("mine")
    val zip = zipOf("my-app/keep.txt" to "theirs")
    expectThat(writer.write(zip, into)).isLeft().isA<ProjectAlreadyExists>()
    expectThat(into.resolve("my-app/keep.txt").readText()).isEqualTo("mine")
}

@Test
fun `a failure midway removes the half-written folder`() {
    // "my-app/a" is written as a file, so creating "my-app/a/b" under it fails
    val zip = zipOf("my-app/a" to "file", "my-app/a/b" to "boom")
    expectThat(writer.write(zip, into)).isLeft().isA<ProjectWriteFailed>()
    expectThat(into.listDirectoryEntries()).isEmpty()
}

@Test
fun `exists checks the name's folder`() {
    into.resolve("my-app").createDirectory()
    expectThat(writer.exists(ProjectName.parse("com.example.my-app").getOrNull()!!, into)).isTrue()
    expectThat(writer.exists(ProjectName.parse("com.example.other").getOrNull()!!, into)).isFalse()
}
```

- [ ] **Step 2: Run to verify it fails**

Run: `./gradlew test --tests '*ZipProjectWriterTest'`
Expected: compilation failure, `ZipProjectWriter` unresolved.

- [ ] **Step 3: Implement `ZipProjectWriter`.** Use two passes:
  1. Read every entry into memory with `ZipInputStream`. Require exactly one top-level folder. Require every entry to resolve inside `into.resolve(top)` after `.normalize()`. Return `ProjectWriteFailed` on any violation, and on a corrupt zip, before touching the disk.
  2. If the top folder exists, return `ProjectAlreadyExists`. Otherwise write the entries. On any `IOException`, delete the top folder recursively and return `ProjectWriteFailed(e.message)`. Mark files named `gradlew` or `mvnw` executable.

  Put this comment on the executable rule: `// ponytail: java.util.zip drops Unix modes, so only gradlew/mvnw get +x (the only executables Launch ships as of 5.2.2); read modes via zipfs "enablePosixFileAttributes" if that changes`.

- [ ] **Step 4: Run to verify it passes**

Run: `./gradlew test --tests '*ZipProjectWriterTest'`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add src/main/kotlin/com/leeturner/mtui/domain/core/ports/ProjectWriter.kt src/main/kotlin/com/leeturner/mtui/adapters/outbound/filesystem src/test/kotlin/com/leeturner/mtui/adapters/outbound/filesystem
git commit --no-gpg-sign -m "Write generated projects from the Launch zip"
```

---

### Task 4: ProjectGenerator

**Files:**
- Create: `src/main/kotlin/com/leeturner/mtui/domain/core/services/ProjectGenerator.kt`
- Test: `src/test/kotlin/com/leeturner/mtui/domain/core/services/ProjectGeneratorTest.kt`

**Interfaces:**
- Consumes: `ProjectCreator` (Task 2), `ProjectWriter` (Task 3), `ProjectName`, `ApplicationType`, `GenerateProjectError`.
- Produces: `@Singleton class ProjectGenerator(creator: ProjectCreator, writer: ProjectWriter)` with `fun generate(type: ApplicationType, name: ProjectName, into: Path): Either<GenerateProjectError, Path>`.

- [ ] **Step 1: Write the failing tests** using hand-written fakes: a `FakeCreator` that records calls and returns a set result, and a `FakeWriter` with a settable `exists` and `write` result.

```kotlin
@Test
fun `existing folder is refused before calling launch`() {
    writer.existing = true
    expectThat(generator.generate(type, name, into)).isLeft().isA<ProjectAlreadyExists>()
        .get { path }.isEqualTo(into.resolve("my-app"))
    expectThat(creator.calls).isEqualTo(0)
}

@Test
fun `launch errors are passed through and nothing is written`() {
    creator.result = ProjectRejected("Invalid package name: MyApp").left()
    expectThat(generator.generate(type, name, into)).isLeft().isA<ProjectRejected>()
    expectThat(writer.writes).isEqualTo(0)
}

@Test
fun `zip from launch is written and the project path returned`() {
    creator.result = byteArrayOf(1, 2).right()
    writer.result = into.resolve("my-app").right()
    expectThat(generator.generate(type, name, into)).isRight().get { value }.isEqualTo(into.resolve("my-app"))
}
```

- [ ] **Step 2: Run to verify it fails**

Run: `./gradlew test --tests '*ProjectGeneratorTest'`
Expected: compilation failure, `ProjectGenerator` unresolved.

- [ ] **Step 3: Implement `generate`** with an `either { }` block: check `writer.exists` → `creator.createProject(...).bind()` → `writer.write(...).bind()`.

- [ ] **Step 4: Run to verify it passes**

Run: `./gradlew test --tests '*ProjectGeneratorTest'`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add src/main/kotlin/com/leeturner/mtui/domain/core/services src/test/kotlin/com/leeturner/mtui/domain/core/services
git commit --no-gpg-sign -m "Add the project generator"
```

---

### Task 5: TUI and command wiring

**Files:**
- Create: `src/main/kotlin/com/leeturner/mtui/adapters/inbound/tui/MtuiApp.kt`
- Modify: `src/main/kotlin/com/leeturner/mtui/MtuiCommand.kt`
- Modify: `src/test/kotlin/com/leeturner/mtui/MtuiCommandTest.kt`

**Interfaces:**
- Consumes: `SelectOptionRetriever` (existing), `ProjectGenerator` (Task 4), `ProjectName` (Task 1).
- Produces:
  - `sealed interface MtuiOutcome { data class Created(val path: Path) : MtuiOutcome; data object Cancelled : MtuiOutcome; data class Failed(val message: String) : MtuiOutcome }`
  - `class MtuiApp(selectOptionRetriever: SelectOptionRetriever, generator: ProjectGenerator, workingDir: Path) : ToolkitApp()` with `val outcome: MtuiOutcome` (starts as `Cancelled`) that can be read after `run()` returns.

- [ ] **Step 1: Update `MtuiCommandTest`.** Delete `testWithCommandLineOption` (the `-v` / "Hi!" test). Keep `unknown option returns a non-zero exit code`. Add:

```kotlin
@Test
fun `help describes the command`() {
    // capture stdout as the deleted test did, run with "--help"
    expectThat(exitCode).isEqualTo(0)
    expectThat(output).contains("Create Micronaut projects from the terminal")
}
```

- [ ] **Step 2: Run to verify it fails**

Run: `./gradlew test --tests '*MtuiCommandTest'`
Expected: FAIL. The help output doesn't contain the description yet.

- [ ] **Step 3: Update `MtuiCommand`.** Set `description = ["Create Micronaut projects from the terminal"]`. Remove `--verbose`. Constructor-inject `SelectOptionRetriever` and `ProjectGenerator`. If PicocliRunner can't constructor-inject the command, use `@Inject lateinit var`. `call()` builds `MtuiApp(retriever, generator, Path.of("").toAbsolutePath())`, calls `run()`, then maps `outcome`:
  - `Created(path)` → `println(path)`, `println("cd ${path.fileName}")`, return `0`
  - `Failed(message)` → `System.err.println(message)`, return `1`
  - `Cancelled` → return `1`

- [ ] **Step 4: Implement `MtuiApp`.** The screen state is a sealed type: `Loading → LoadFailed(message) | Form(options, error: String?) → Generating → done`. Specific requirements:
  - `onStart()` fetches select options off the render thread, using a `CompletableFuture` as Dan's `SpringInitializrTui` does, and applies the result with `runner().runOnRenderThread { ... }`.
  - `LoadFailed`: show the message. Any key sets `outcome = Failed(message)` and quits.
  - `Form`: one `textInput` for the name, plus read-only labels for the default type, language, build, test framework and JDK from `SelectOptions`. On Enter: `ProjectName.parse`. A `Left` sets `error` and stays on the form. A `Right` → `Generating`, then `generator.generate(options.defaultType, name, workingDir)` off the render thread. A `Left` shows its `message` back on the form. A `Right` sets `outcome = Created(path)` and quits.
  - `Generating`: Enter and Esc are ignored (Review Focus 5).
  - Esc or Ctrl+C on `Loading` or `Form` sets `outcome = Cancelled` and quits.
  - Layout, styling and the spinner are up to the builder (see the spec's "Left to the builder").

- [ ] **Step 5: Run checks**

Run: `./gradlew check`
Expected: BUILD SUCCESSFUL

- [ ] **Step 6: Manual run against the real Launch API**, from a scratch directory: `./gradlew installDist`, then `cd $(mktemp -d)` and run `<repo>/build/install/mtui/bin/mtui`. Check each item:
  - [ ] The loading screen, then the form, show the defaults (DEFAULT / JAVA / GRADLE_KOTLIN / JUNIT / JDK_25 at the time of writing).
  - [ ] `com.example.my-app` + Enter: the TUI closes and prints the path and `cd my-app`. The exit code is 0. `./my-app/gradlew build` runs.
  - [ ] Run again with the same name: the form shows "… already exists".
  - [ ] `MyApp`: the form shows `Invalid package name: MyApp`.
  - [ ] `my app`: the form shows the characters message, and no request is made.
  - [ ] Pressing Enter several times quickly while generating creates exactly one project.
  - [ ] Esc on the form: exit code 1, and the terminal works normally afterwards (echo and cursor are back).
  - [ ] With `MICRONAUT_HTTP_SERVICES_MICRONAUT_LAUNCH_URL=http://localhost:1`: the loading screen shows an error, any key exits, and the exit code is 1.

- [ ] **Step 7: Commit**

```bash
git add src/main/kotlin/com/leeturner/mtui src/test/kotlin/com/leeturner/mtui/MtuiCommandTest.kt
git commit --no-gpg-sign -m "Add the walking skeleton TUI"
```

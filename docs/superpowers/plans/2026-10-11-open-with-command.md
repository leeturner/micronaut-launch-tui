# Open With Your Command Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** After a project is created, `mtui` runs the user's command (set by `--open` or `MTUI_OPEN`) with the project path as its last argument.

**Architecture:** Two top-level functions in a new file in the root (composition root) package. `openCommand` picks and splits the command. `openProject` runs it with `ProcessBuilder`, inheriting the terminal. `MtuiCommand` gets an `--open` option and calls both in its `Created` branch, after the TUI has closed. There's no port, adapter or interface.

**Tech Stack:** Kotlin, Micronaut + Picocli, Arrow `Either`, JUnit 5, Strikt.

**Spec:** `docs/superpowers/specs/2026-10-11-open-with-command-design.md`

## Global Constraints

- The option is `--open <command>`. Without it, `MTUI_OPEN` is used. An explicit `--open` wins, even `--open ""`, which turns opening off.
- The command is split on whitespace. Leading and trailing whitespace is trimmed, and blank means not set. There's no quote parsing.
- The project's absolute path is appended as the last argument.
- The command inherits stdin, stdout and stderr, and `mtui` waits for it to finish.
- The path and the `cd` line are printed **before** the command runs, exactly as today.
- The failure message goes to stderr, and the exit code is 1: `Could not open the project with "<command>": <reason>`. `<command>` is the split command joined with single spaces. `<reason>` is the `IOException` message, or `it exited with code <n>`.
- Only `MtuiOutcome.Created` runs the command.
- No new dependencies. Catch `IOException` only (detekt flags generic catches).
- Gradle runs on Java 25. If kapt fails with a javaparser `NoSuchMethodError`, use `JAVA_HOME=~/.sdkman/candidates/java/25-tem`. `./gradlew check` passes after every task. Commit with `git commit --no-gpg-sign`.

## Review Focus

1. **`--open ""` while `MTUI_OPEN` is set:** nothing runs. (Task 1 test.)
2. **Padded values such as `"  code   -n "`:** become `[code, -n]`, with no empty arguments. (Task 1 test.)
3. **A project path containing a space:** it reaches the command as a single argument. (Task 1 test.)
4. **Cancelling or a load failure:** the command never runs. (Task 2 manual check.)
5. **GUI launchers:** `idea` and `code` return straight away, so `mtui` doesn't hang. (Task 2 manual check.)

---

### Task 1: Choosing and running the command

**Files:**
- Create: `src/main/kotlin/com/leeturner/mtui/OpenCommand.kt`
- Test: `src/test/kotlin/com/leeturner/mtui/OpenCommandTest.kt`

**Interfaces:**
- Produces: `internal fun openCommand(option: String?, env: String?): List<String>?` (null means don't open)
- Produces: `internal fun openProject(command: List<String>, path: Path): Either<String, Unit>` (Left is the `<reason>`)

- [ ] **Step 1: Write the failing tests** in `OpenCommandTest`. Use Strikt and `@TempDir`.

```kotlin
@Test fun `the option wins over the environment`() =
    expectThat(openCommand("code -n", "idea")).isEqualTo(listOf("code", "-n"))
@Test fun `falls back to the environment`() =
    expectThat(openCommand(null, "idea")).isEqualTo(listOf("idea"))
@Test fun `neither set means no command`() = expectThat(openCommand(null, null)).isNull()
@Test fun `an empty option turns opening off`() = expectThat(openCommand("", "idea")).isNull()
@Test fun `blank means no command`() = expectThat(openCommand(null, "   ")).isNull()
@Test fun `splits on runs of whitespace and trims`() =
    expectThat(openCommand("  code   -n ", null)).isEqualTo(listOf("code", "-n"))

// `test -d <path>` succeeds only when the path is passed as the last argument and is a directory
@Test fun `runs the command with the project path`(@TempDir dir: Path) =
    expectThat(openProject(listOf("test", "-d"), dir)).isRight()
@Test fun `a path with a space is one argument`(@TempDir dir: Path) {
    val project = Files.createDirectory(dir.resolve("my demo"))
    expectThat(openProject(listOf("test", "-d"), project)).isRight()
}
@Test fun `a non-zero exit is a failure`(@TempDir dir: Path) =
    expectThat(openProject(listOf("false"), dir)).isLeft().get { value }.isEqualTo("it exited with code 1")
@Test fun `a missing program is a failure`(@TempDir dir: Path) =
    expectThat(openProject(listOf("mtui-no-such-command"), dir)).isLeft().get { value }.contains("mtui-no-such-command")
```

Use the Strikt Arrow assertions the other tests already use for `isRight`/`isLeft`.

- [ ] **Step 2: Run them to see them fail**

Run: `./gradlew test --tests '*OpenCommandTest'`
Expected: FAIL to compile (`openCommand` and `openProject` are unresolved)

- [ ] **Step 3: Implement both functions in `OpenCommand.kt`**

`openCommand`: `(option ?: env)`, trim it, return null if blank, otherwise split on `Regex("\\s+")`. `openProject`: `ProcessBuilder(command + path.toString()).inheritIO().start().waitFor()`. A non-zero code is `Left("it exited with code $code")`, and an `IOException` is `Left(e.message ?: e.toString())`. Add a one-line comment on `openCommand` saying that quoted arguments aren't supported.

- [ ] **Step 4: Run the tests to see them pass**

Run: `./gradlew test --tests '*OpenCommandTest'`
Expected: PASS

- [ ] **Step 5: Run `./gradlew check`, then commit**

```bash
git add src/main/kotlin/com/leeturner/mtui/OpenCommand.kt src/test/kotlin/com/leeturner/mtui/OpenCommandTest.kt
git commit --no-gpg-sign -m "Run a command to open the created project"
```

### Task 2: The `--open` option

**Files:**
- Modify: `src/main/kotlin/com/leeturner/mtui/MtuiCommand.kt` (the class and the `Created` branch of `call()`)
- Modify: `src/test/kotlin/com/leeturner/mtui/MtuiCommandTest.kt`
- Modify: `docs/roadmap.md` (slice 6 status → `Done`)

**Interfaces:**
- Consumes: `openCommand` and `openProject` from Task 1

- [ ] **Step 1: Write the failing test** in `MtuiCommandTest`, alongside `help describes the command`, using the same setup:

```kotlin
@Test fun `help describes the open option`() {
    // run with "--help", capture stdout as the existing test does
    expectThat(baos.toString()).contains("--open").contains("MTUI_OPEN")
}
```

- [ ] **Step 2: Run it to see it fail**

Run: `./gradlew test --tests '*MtuiCommandTest'`
Expected: FAIL (the help has no `--open`)

- [ ] **Step 3: Add the option and wire it in**

Add `@Option(names = ["--open"], paramLabel = "<command>", description = ["Command to open the project with once it is created, e.g. idea. Defaults to the MTUI_OPEN environment variable."]) var open: String? = null`. Leave out picocli's `defaultValue`: the env fallback lives in `openCommand`, which is what makes `--open ""` turn opening off. In the `Created` branch, keep the two `println`s, then call `openCommand(open, System.getenv("MTUI_OPEN"))`. If it's null, return 0. Otherwise fold `openProject(command, outcome.path)`: Left prints the Global Constraints message to `System.err` and returns 1, and Right returns 0.

- [ ] **Step 4: Run the tests to see them pass**

Run: `./gradlew check`
Expected: PASS, including `ArchitectureTest`

- [ ] **Step 5: Check it by hand**

Run `./gradlew run --args='--open "echo opening"'` (or build and run the binary) and create a project. The path and the `cd` line are printed, then `opening /abs/path/<name>`, and the exit code is 0. Do the same with `--open mtui-no-such-command`: you should see the error on stderr, exit code 1, and the project folder still there. Pressing Esc to cancel prints nothing extra. If IntelliJ's `idea` launcher is on PATH, `MTUI_OPEN=idea` opens the project and `mtui` returns straight away.

- [ ] **Step 6: Mark slice 6 done in `docs/roadmap.md` and commit**

```bash
git add src/main/kotlin/com/leeturner/mtui/MtuiCommand.kt src/test/kotlin/com/leeturner/mtui/MtuiCommandTest.kt docs/roadmap.md
git commit --no-gpg-sign -m "Open the created project with --open or MTUI_OPEN"
```

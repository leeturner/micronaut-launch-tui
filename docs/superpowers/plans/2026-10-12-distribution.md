# Distribution Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build `mtui` as a GraalVM native image for macOS arm64, Linux x86_64 and Windows x86_64, and publish the binaries to a GitHub Release when a `v*` tag is pushed.

**Architecture:** The project version moves from `build.gradle.kts` into `gradle.properties` as `version=dev`, so the release can override it with `-Pversion=…`. `processResources` copies the version into `application.properties`, and a picocli version provider bean reads it, which needs no reflection in the native image. The spike's `graalvmNative` block and reachability metadata come across unchanged. A new `release.yml` runs a build matrix and then a release job.

**Tech Stack:** Kotlin, Gradle (Kotlin DSL), Micronaut + Picocli, GraalVM CE 25 native image, GitHub Actions.

**Spec:** `docs/superpowers/specs/2026-10-12-distribution-design.md`

## Global Constraints

- The binary is `mtui` (`mtui.exe` on Windows). The archives are `mtui-<version>-macos-aarch64.tar.gz`, `mtui-<version>-linux-x86_64.tar.gz` and `mtui-<version>-windows-x86_64.zip`, with the binary at the top level.
- `<version>` is the tag without its leading `v`. Untagged builds are version `dev`.
- The release runs on a `v*` tag push. `workflow_dispatch` runs the builds and checks but not the release job. No tests run in `release.yml`.
- GraalVM Community Edition, Java 25. Gradle must run on Java 25, because kapt breaks on 26.
- Actions are pinned to a full commit SHA with a `# vX.Y.Z` comment, as `build.yml` pins `gradle/actions`.
- Locally: `JAVA_HOME=~/.sdkman/candidates/java/25-tem`, and for native builds also `GRAALVM_HOME=~/.sdkman/candidates/java/25.4.4.1-graalce`. `./gradlew check` passes after every task. Commit with `git commit --no-gpg-sign`. Claude never pushes tags.

## Review Focus

1. **The version in the native binary:** `--version` must print the filtered value (`dev`, or the `-Pversion` value), not the literal `@version@`. (Task 2 verification.)
2. **Missing native metadata on Linux:** it only shows when the TUI starts, not with `--help` or `--version`. (Task 2, Linux container check.)
3. **The archive layout:** unpacking gives `mtui` straight away, with no folder around it, and keeps the executable bit. (Task 3, local archive check.)
4. **Windows paths in the workflow:** the steps must work under `bash` on Windows (`shell: bash`), and the binary is `mtui.exe`. (Task 3 review.)
5. **The release job on a manual run:** it must be skipped, so nothing is published. (Task 3 `if:` condition.)

---

### Task 1: `mtui --version` prints the project version

**Files:**
- Modify: `gradle.properties` (add `version=dev`)
- Modify: `build.gradle.kts` (remove `version = "0.1"`, filter `application.properties`)
- Modify: `src/main/resources/application.properties` (add `mtui.version=@version@`)
- Create: `src/main/kotlin/com/leeturner/mtui/MtuiVersion.kt`
- Modify: `src/main/kotlin/com/leeturner/mtui/MtuiCommand.kt` (add `versionProvider = MtuiVersion::class` to `@Command`)
- Modify: `README.md`, `docs/tapes/config.tape` (`-0.1-all.jar` → `-dev-all.jar`)
- Test: `src/test/kotlin/com/leeturner/mtui/MtuiCommandTest.kt`

**Interfaces:**
- Produces: the Gradle project version comes from `gradle.properties` and can be overridden with `-Pversion=<v>`. `mtui --version` prints that one line.

- [ ] **Step 1: Write the failing test** `` `version prints the project version` `` in `MtuiCommandTest`. It follows the `--help` capture pattern there and passes `"--version"`. Expect exit code `0` and `baos.toString().trim()` to equal `"dev"`.
- [ ] **Step 2: Run it.** `./gradlew test --tests '*MtuiCommandTest*'` should FAIL, because the output is empty.
- [ ] **Step 3: Implement.**
  - In `gradle.properties`, add `version=dev`, and delete `version = "0.1"` from `build.gradle.kts`. A `-P` on the command line beats `gradle.properties`.
  - In `build.gradle.kts`:
    ```kotlin
    tasks.processResources {
      inputs.property("version", project.version)
      filesMatching("application.properties") { filter { it.replace("@version@", project.version.toString()) } }
    }
    ```
  - `MtuiVersion` is `@Singleton class MtuiVersion(@Value("\${mtui.version}") private val version: String) : CommandLine.IVersionProvider` and returns `arrayOf(version)`. Because it's a bean, Micronaut's picocli factory builds it without reflection.
- [ ] **Step 4: Run it.** The test passes, and `./gradlew check` passes.
- [ ] **Step 5: Check the override.** Run `./gradlew shadowJar -Pversion=9.9.9`. The jar should be `build/libs/micronaut-launch-tui-9.9.9-all.jar`, and `java -jar` on it with `--version` should print `9.9.9`. Then run `./gradlew shadowJar` again, so the `-dev-all.jar` the README names exists.
- [ ] **Step 6: Update the jar name** in `README.md` (both lines) and `docs/tapes/config.tape` to `micronaut-launch-tui-dev-all.jar`.
- [ ] **Step 7: Commit:** "Print the project version with --version".

### Task 2: Build `mtui` as a native image

**Files:**
- Modify: `build.gradle.kts` (the `graalvmNative` block from `spike/native-image`)
- Create: `src/main/resources/META-INF/native-image/com.leeturner/mtui/reachability-metadata.json` (from `spike/native-image`)
- Modify: `src/main/resources/logback.xml` (root `error`; this change is already in the working tree)

**Interfaces:**
- Consumes: Task 1's version.
- Produces: `./gradlew nativeCompile` writes `build/native/nativeCompile/mtui`, or `mtui.exe` on Windows.

- [ ] **Step 1: Bring over the spike.** Run `git checkout spike/native-image -- src/main/resources/META-INF`, and copy its `graalvmNative { binaries { named("main") { imageName = "mtui"; buildArgs.add("--enable-native-access=ALL-UNNAMED") } } }` block into `build.gradle.kts`, after the shadowJar block, with a one-line comment for each setting.
- [ ] **Step 2: Build on macOS.** Run `./gradlew nativeCompile -Pversion=0.0.1` with `GRAALVM_HOME` set. `build/native/nativeCompile/mtui --version` should print `0.0.1`.
- [ ] **Step 3: Smoke test in tmux** from an empty scratch directory. Type a name, press Enter (the picker shows features), press `p` (preview shows files), then Esc and Enter. The project is created and the exit code is `0`, with no stack trace and no `WARNING:` lines.
- [ ] **Step 4: Check Linux in a container.** Build in `ghcr.io/graalvm/native-image-community:25` (linux/arm64; it runs the same native-image code path, and x86_64 under emulation is too slow), with the repo mounted. Copy the source to `/tmp` in the container so `build/` isn't shared with the macOS build. Run `./gradlew nativeCompile`, then start the binary under `script -qc` with `TERM=xterm-256color`. It should reach the name screen ("Create a Micronaut project") without an exception. Any missing metadata goes into the JSON file.
- [ ] **Step 5:** `./gradlew check` passes.
- [ ] **Step 6: Commit:** "Build mtui as a GraalVM native image" (include `logback.xml`, with a note in the message that the log keeps only errors).

### Task 3: Release workflow

**Files:**
- Create: `.github/workflows/release.yml`

**Interfaces:**
- Consumes: Task 2's `nativeCompile`, and Task 1's `-Pversion`.

- [ ] **Step 1: Write `release.yml`.**
  - Triggers: `push: tags: ['v*']` and `workflow_dispatch`. Top-level `permissions: contents: read`; the release job alone gets `contents: write`.
  - `build` job, a matrix over `{os, platform, ext, archive}`:
    - `macos-latest` / `macos-aarch64` / `''` / `tar.gz`
    - `ubuntu-latest` / `linux-x86_64` / `''` / `tar.gz`
    - `windows-latest` / `windows-x86_64` / `.exe` / `zip`
  - Build steps, all with `shell: bash`:
    - checkout
    - `graalvm/setup-graalvm` with `java-version: '25'` and `distribution: 'graalvm-community'` (it sets up MSVC on Windows itself; add `ilammy/msvc-dev-cmd` only if the first Windows run needs it)
    - `gradle/actions/setup-gradle` at the SHA `build.yml` uses
    - `VERSION`: `${GITHUB_REF_NAME#v}` on a tag, `dev` otherwise
    - `./gradlew nativeCompile -Pversion=$VERSION`
    - check that `build/native/nativeCompile/mtui$ext --version` prints exactly `$VERSION`
    - archive it with `tar -czf` or `7z a` (on Windows) from inside `build/native/nativeCompile`, so the binary sits at the archive root
    - `actions/upload-artifact`
  - `release` job: `needs: build` and `if: startsWith(github.ref, 'refs/tags/v')`. It runs `actions/download-artifact` with `merge-multiple: true`, then `softprops/action-gh-release` with the three archives and `generate_release_notes: true`.
- [ ] **Step 2: Lint.** Run `docker run --rm -v "$PWD:/repo" -w /repo rhysd/actionlint:latest`. It should print nothing.
- [ ] **Step 3: Check the archive locally.** Run the macOS archive command from Step 1 on Task 2's binary, unpack it in an empty directory, and run `./mtui --version`. It should work with no `chmod`.
- [ ] **Step 4: Commit:** "Release native binaries from a v* tag".

### Task 4: README and roadmap

**Files:**
- Modify: `README.md`, `docs/roadmap.md`

- [ ] **Step 1: README.**
  - Add an "Install" section before "Build and run": download the archive for your platform from the latest release at `https://github.com/leeturner/micronaut-launch-tui/releases/latest`, unpack it, and move `mtui` onto your `PATH`.
  - The macOS note: `xattr -d com.apple.quarantine mtui` if Gatekeeper blocks it.
  - Windows is built but untested.
  - Remove "There are no prebuilt downloads yet".
  - Under "Development", add the native build: GraalVM 25, `./gradlew nativeCompile`, binary at `build/native/nativeCompile/mtui`.
- [ ] **Step 2: Roadmap.**
  - Set slice 8's status to `Done`, with the "Done when" column naming the platforms and the `v*` tag.
  - "Possible later" gains: a Homebrew tap, SDKMAN, macOS signing and notarisation, Linux arm64 and macOS Intel builds, and a Linux binary for older glibc or a static musl build.
- [ ] **Step 3: Commit:** "Document installing the native binaries".

## After the plan

The user merges, runs the workflow by hand from `main` (`gh workflow run release.yml`) to check Windows and Linux, then pushes `v0.2.0`.

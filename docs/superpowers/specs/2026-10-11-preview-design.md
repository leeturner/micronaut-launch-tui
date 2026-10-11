# Slice 5: Preview, and tightening ports and adapters

Part of the [roadmap](../../roadmap.md). This is slice 5 of 8. It builds on
[slice 4](2026-10-11-micronaut-version-design.md).

The slice has two parts, built in this order:

1. **Architecture:** put the ports and adapters boundaries back where they
   should be, and add ArchUnit tests so they stay there. Nothing the user sees
   changes.
2. **Preview:** browse the generated files before creating the project.

## Why

**Architecture.** After four slices the boundaries are mostly sound. The domain
imports no adapter, Micronaut HTTP or TamboUI code, and the TUI never touches
an outbound adapter. But some things have drifted, and nothing stops more drift:

- Domain error types carry an HTTP `status: Int?`
  (`FeaturesError`, `VersionError`, `UnexpectedSelectOptionRetrievalError`,
  `UnexpectedProjectCreationError`). Production code never reads it; only the
  adapter tests do.
- `MtuiApp` (296 lines) has become the application service. It decides how
  start-up loads, when features need re-fetching, and how a version switch
  re-maps the choices. None of that can be tested without the TUI.
- The feature search rule from the roadmap (a substring of the name, title or
  description, ignoring case) lives in the TUI's `FeaturePicker`.
- There are three catalog ports (`SelectOptionRetriever`, `FeatureRetriever`,
  `VersionRetriever`) for what is really one Launch catalog. Each has its own
  error type, spread across unrelated files (`FeaturesError` is in
  `Feature.kt`, `VersionError` is in `MicronautVersion.kt`). `CatalogLoader`
  then flattens them all into `CatalogError(message)` anyway.
- `CatalogLoader` and `ProjectGenerator` are annotated with
  `jakarta.inject.Singleton`, so the domain depends on a framework.

The user considered splitting the build into Gradle modules to enforce the
boundaries at compile time. They chose ArchUnit tests instead, so the fix can
be driven test first, without moving the kapt and OpenAPI build setup across
modules.

**Preview.** The user occasionally looks at a preview of a newer Micronaut
version when upgrading an older project. They read the **build file** to see
the new plugin and dependency versions and any new dependencies, then **copy
them out** into the old project. Often they don't create a project at all.

## Part 1: Architecture

The user's decisions:

- **ArchUnit, not Gradle modules.**
- **The domain is framework-free.** `@Singleton` comes off `CatalogLoader` and
  `ProjectGenerator`. A Micronaut `@Factory` builds them instead.
- **The findings above all get fixed in this slice.**
- **No behaviour changes.** Running `mtui` looks and works exactly as it does
  after slice 4.
- **Not doing:** Gradle modules, or inbound port interfaces (each would have
  only one implementation; the TUI keeps calling the domain services
  directly).

### ArchUnit rules

Add `com.tngtech.archunit:archunit-junit5` (the current release) through
`gradle/libs.versions.toml`, as a test dependency. One test class holds the
rules, importing production classes only (not tests).

The layers, by package under `com.leeturner.mtui`:

| Layer | Packages |
|-------|----------|
| Domain model | `domain.core.model` |
| Domain services and ports | `domain.core.services`, `domain.core.ports` |
| Adapter: tui | `adapters.inbound.tui` |
| Adapter: http | `adapters.outbound.http` (including the generated `client` package) |
| Adapter: filesystem | `adapters.outbound.filesystem` |
| Composition root | the root package: `MtuiCommand` and the new factory. It may depend on anything, and nothing depends on it. |

Rules:

1. **Onion layering.** Use ArchUnit's `onionArchitecture()`, or equivalent
   layered rules: the model depends on nothing else, services and ports depend
   only on the model, and adapters never depend on each other. This passes
   today. From now on it stops new leaks, for example the TUI reaching the
   generated Launch client.
2. **The domain depends only on the domain, Kotlin, the JDK and Arrow.** This
   **fails today**: `jakarta.inject.Singleton` is on the two services, and the
   kapt-generated `$…$Definition` bean classes in the services package depend
   on `io.micronaut`. It passes once the factory replaces `@Singleton`.
   *(Note for the builder: Kotlin bytecode also references
   `org.jetbrains.annotations`; allow it if the rule trips on it.)*

Write both rules first and watch rule 2 fail before the factory goes in.

### The factory

A Micronaut `@Factory` in the root package, next to `MtuiCommand`, which is
already the composition root. It has `@Singleton` methods that build
`CatalogLoader` and `ProjectGenerator` from their ports. *(Claude's call: the
location.)*

### HTTP status out of the domain

Remove `status` from every domain error type. The adapter tests that assert on
it change first: they still check that a failure comes back as an error with
Launch's message, not an exception.

### One catalog port

*(Claude's call. This was raised with the user, who didn't object.)*

- Replace `SelectOptionRetriever`, `FeatureRetriever` and `VersionRetriever`
  with a single port, for example `LaunchCatalog`, with the three operations.
  One adapter implements it. The mapping code can stay in separate files.
- The port returns the existing `CatalogError(message)` directly, so
  `FeaturesError`, `VersionError` and the `SelectOptionsError` hierarchy go.
  Nothing branches on the kind of catalog failure today.
- `GenerateProjectError` stays a sealed type, without `status`.
- Each remaining error type lives in its own file, or in the file of the one
  model it belongs to. Nothing stays tucked into an unrelated model's file.

### Logic out of `MtuiApp`

Each piece moves into the domain, driven by a domain test written first. The
exact function and service names are the builder's.

| Today in the TUI | Moves to | Test that drives it |
|---|---|---|
| Start-up: release `/select-options` plus both servers' version labels, using an extra `CompletableFuture` in `MtuiApp` | One `CatalogLoader` operation that returns the options and the version labels together, keeping today's timeout behaviour | Returns both; a failed or slow version still gives the fallback label; failed options are an error |
| `featuresFor` triple: features are only re-fetched when the server, type or language changes | A domain function on `ProjectOptions` | Changing build, test or JDK doesn't need a re-fetch; changing server, type or language does |
| Version switch: load the new server's options, then `onServer` | A `CatalogLoader` operation that returns the new `SelectOptions` and the carried-over `ProjectOptions` | Choices carried over by value; a failure is an error and nothing changes |
| `Feature.matches` in `FeaturePicker` | The domain model (`Feature`) | Name, title and description match, ignoring case; the term is trimmed |

These stay in the TUI because they are presentation: highlighting, scrolling,
grouping features for display, `SHORT_TYPE_LABELS`, the options summary line,
and caching the `FeaturePickerScreen`.

## Part 2: Preview

### Getting there

- **From the feature picker**, `p` previews exactly what Create would produce:
  the name, the chosen version and options, and the selected features. While
  the search box has focus, `p` is typed into it, like every other letter.
- **From the name screen**, `p` works wherever the option-row keys work, that
  is, when the focus is on an option row. In the name box, `p` is typed, the
  same as `h`, `j`, `k` and `l` are today. The name must be valid, and the
  usual message shows if it isn't. The "folder already exists" check doesn't
  apply, because nothing is written. The preview uses **no features**.
  *(Claude's call: the use is looking at a version's build file, and this
  avoids carrying a selection that might belong to another type or language.)*
- While the preview loads, a spinner shows "Fetching preview…" where that
  screen shows status today. Keys are ignored except Ctrl+C, which quits.
- If Launch fails, its message shows on that screen's status line, as a failed
  feature fetch or generate does today. For example, a 400 for clashing
  features reads the same as it does on Create. On the name screen, the focus
  stays on the option row (as after a failed version switch), not the name
  box, so pressing `p` again retries instead of typing a "p". *(Claude's
  call.)*
- Esc on the preview goes back to the screen it came from, with everything as
  it was left (name, options, selection, search).
- Add `p preview` to both screens' key hints.

### The preview screen

The user chose this layout from mockups (option "B"): **the file fills the
screen, with no borders or side panel**, so selecting with the mouse copies
clean lines.

```
build.gradle.kts  (3/13)  5.2.2 · Default · Kotlin · Gradle Kotlin · JUnit · 25
plugins {
    id("org.jetbrains.kotlin.jvm") version "2.3.21"
    id("io.micronaut.application") version "5.0.2"
    id("com.gradleup.shadow") version "9.4.1"
}

dependencies {
    ksp("io.micronaut.data:micronaut-data-processor")
    implementation("io.micronaut.data:micronaut-data-jdbc")
j/k scroll · f files · [ ] prev/next · Esc back
```

- **It opens on the build file:** `build.gradle`, `build.gradle.kts` or
  `pom.xml`, whichever is at the root. If there is none, it opens on the first
  file.
- **The top line** shows the file's path, its position in the list, and the
  same options summary as the picker header.
- **Scrolling:** `j`/`k` and ↑/↓ move a line, PgUp/PgDn move a page. *(Claude's
  call: no other scroll keys yet.)*
- **Mouse selection must work.** The screen must not capture the mouse in a
  way that stops the terminal's own text selection. Long lines must not be
  silently cut off. *(Left to the builder: wrap them or scroll sideways,
  whichever TamboUI makes reliable for copying.)*

### Other files

The user asked that other files be easy to get to.

- `[` and `]` step to the previous and next file, in path order. That is a
  plain case-sensitive sort, so `.gitignore` and `README.md` come before
  `build.gradle.kts`, as in the "(3/13)" example. *(Claude's call.)* They stop at
  the ends and don't wrap, like the option rows.
- `f` opens the file list over the file, with the current file highlighted:

  ```
  ╭ Files ─────────────────────────────────────╮
  │ prop█                                      │
  │ gradle.properties                          │  ← highlighted
  │ gradle/wrapper/gradle-wrapper.properties   │
  │ src/main/resources/application.properties  │
  ╰────────────────────────────────────────────╯
  type to filter · ↑/↓ move · Enter open · Esc close
  ```

  Typing filters by a substring of the path, ignoring case. ↑/↓ move, Enter
  opens the highlighted file and closes the list, and Esc closes it without
  changing file. `/` does the same as `f`, to keep the roadmap's "`/` starts a
  search wherever a list can be searched". *(Claude's call: letters go to the
  filter, so `j`/`k` don't move in this list. That trades the roadmap's
  `j`/`k` rule for one-step filtering.)*
- **Binary files are left out of the list.** Launch returns `null` for their
  contents (`gradle-wrapper.jar`, `maven-wrapper.jar`). So a Gradle Kotlin
  project lists 13 files, not 14.

### Structure

- **A domain type for the previewed project**: its files as path and text,
  sorted by path, and how to find the build file. Its lookup of the build file
  is unit tested.
- **The preview is an operation on the existing `ProjectCreator` port and its
  Launch adapter** (`/preview/{type}/{name}`, generated operation
  `previewApp`). *(Claude's call: it takes exactly the same inputs as
  `createProject` and shares its value mapping, so a second port and adapter
  would duplicate both.)* It returns `GenerateProjectError` on failure, so a
  400 is a `ProjectRejected` with Launch's message, as on Create.
- **`ProjectGenerator` gets a `preview` operation.** It doesn't check whether
  the folder exists.
- **The adapter drops files whose contents are `null`.** The OpenAPI spec says
  the values are strings, so check that the generated model tolerates `null`
  values. If it doesn't, adjust the generation, the way `build.gradle.kts`
  already patches other models.
- The preview screen and the file-list state live in `adapters.inbound.tui`. As
  with `FeaturePicker` and `OptionsForm`, the state is kept free of TamboUI so
  it can be unit tested. That state covers the current file, `[`/`]`, the
  filter, the highlight and the scroll position.
- Requests go to the chosen server through the existing `onServer` routing.

## Testing

Follow the existing style: WireMock with recorded payloads, Strikt, and fakes
for the domain service tests.

- **Architecture:** the two ArchUnit rules. Rule 2 is red before the factory.
- **Domain:** each row of the "Logic out of `MtuiApp`" table, the build-file
  lookup, and `ProjectGenerator.preview` (passes through, no folder check).
- **Adapter:** preview from a recorded payload, with a `null`-content file
  dropped; a 400 becomes `ProjectRejected` with Launch's message; the preview
  goes to the snapshot server when it is chosen. The existing adapter tests,
  minus their `status` assertions.
- **TUI state:** opening on the build file, `[`/`]` stopping at the ends, the
  filter, and Enter on a filtered list opening the right file.

Screens still don't need automated tests. A manual run against real Launch is
the check:

1. Start `mtui`, choose the snapshot and Kotlin with Gradle Kotlin, type a
   name, Tab to an option row and press `p`. Check it opens on
   `build.gradle.kts` showing the snapshot's versions.
2. Select a few lines with the mouse and paste them somewhere. Check they come
   out clean, with no borders or other text.
3. Press `f`, type `prop`, open `gradle.properties`. Then use `[` and `]`.
4. Esc back. Enter the picker, select `data-jdbc`, press `p`. Check the build
   file has the data dependencies. Esc, then generate as normal.
5. Run through slice 4's manual check to confirm nothing else changed.

## Roadmap change

Update slice 5's row in `docs/roadmap.md`: the preview is reachable from the
name screen and the picker, and ArchUnit tests guard the ports and adapters
boundaries. Mark it done when the slice merges.

## Out of scope

- Syntax highlighting (slice 7, polish).
- A key that copies a whole file to the clipboard. The mouse covers the use.
  Add it if selecting by hand proves awkward.
- Comparing against an existing project's build file. This is close to the
  parked "feature diff" idea on the roadmap.
- Gradle modules and inbound port interfaces.
- Generating from the preview screen. Esc back and generate from the picker.

## Left to the builder

- Names of the new port operations, domain functions and types.
- Wrapping or sideways scrolling for long lines, provided copying works.
- TamboUI details for the popup file list and the spinner.
- How the merged catalog adapter's tests are split across files.

# Slice 1: Walking skeleton

Part of the [roadmap](../../roadmap.md). This is slice 1 of 7.

## Why

The point of `mtui` is to make choosing Micronaut features less painful,
because there are too many to find. That is slice 2. Slice 1 exists to give
the feature picker somewhere to live. It proves the whole path works
(TamboUI inside a Micronaut/Picocli app, the Launch API, writing the project
to disk) with as little UI as possible. The patterns it sets (screens,
ports, adapters, error handling) are what later slices copy.

Keep it small. Every hour spent here is an hour not spent on the picker.

## What it does

1. The user runs `mtui` from a parent folder, e.g. `~/dev`.
2. A loading screen shows while `/select-options` is fetched, using the
   existing `SelectOptionRetriever`.
3. A form screen shows:
   - one text field for the project name, in the same format as
     `mn create-app`: `com.example.my-app`. The last segment is the folder
     and artifact name, and the rest is the package. Launch derives both from
     the single value, so `mtui` passes it straight through.
   - the Launch defaults for application type, language, build tool, test
     framework and JDK, shown **read-only** so the user knows what they'll
     get.
4. On Enter, it calls `/create/{type}/{name}` with the default type and no
   other parameters, so Launch applies its own defaults.
5. The zip is extracted into the **current working directory**. Launch's zip
   already contains a top-level `my-app/` folder, so this produces
   `./my-app/` just like `mn create-app`.
6. The TUI closes. `mtui` prints the absolute path of the new project and
   `cd my-app`, then exits 0.

## Behaviour that matters

- **Folder already exists:** if `./<last segment>/` exists, refuse before
  calling the API. Show the message in the form and let the user change
  the name. Never overwrite or merge into an existing folder.
- **Invalid name:** Launch returns 400 with a readable message, e.g.
  `Invalid package name: MyApp` or `Invalid project name: Cannot create a
  valid package name for [123app]...`. Show that message in the form and let
  the user fix it. Don't re-implement Launch's name rules. The one exception:
  names with characters outside `[A-Za-z0-9._-]` (spaces, `!`) make Launch
  return 404 "Page Not Found", which is useless, so reject those in the form
  before calling the API.
- **Empty name:** Enter does nothing, or shows "Name required".
- **Launch unreachable or `/select-options` fails:** show the error on the
  loading screen. Any key exits with a non-zero code. No retry loop.
- **`/create` fails for any reason other than a 400:** show the error in the
  form and let the user try again.
- **Executable bits:** `gradlew` / `mvnw` must be executable after
  extraction. Launch's zip stores Unix modes (`gradlew` is `rwxr-xr-x`), but
  `java.util.zip` ignores them, so the builder must preserve them, either by
  reading the stored mode or by restoring it for the wrapper scripts.
- **Zip safety:** every entry must resolve inside the target folder. Reject
  the whole zip if any entry escapes, and fail before writing anything.
- **Partial failure:** if extraction fails midway, delete the partially
  written project folder. That folder didn't exist before, so deleting it
  loses nothing of the user's.
- **Quit:** Esc or Ctrl+C on any screen exits cleanly with a non-zero code
  and restores the terminal.

## Structure

This follows the existing hexagonal layout. These are decisions:

- a new outbound port in `domain/core/ports` for creating a project
  (inputs: type and name; output: the zip bytes or a typed error, using
  Arrow `Either` like `SelectOptionRetriever`).
- a Launch HTTP adapter for that port in `adapters/outbound/http`, using the
  generated `MicronautLaunchDefaultApi` client. Errors are mapped to a sealed
  error type like `SelectOptionsError`. A 400 carries Launch's message text.
  Launch-specific details:
  - success is **201**, not 200. Treat any 2xx as success.
  - the readable 400 message is at `_embedded.errors[0].message`. The
    top-level `message` is just "Bad Request".
  - the spec declares the `/create` response as `application/zip: {}` with
    no schema, so the generated `createApp` returns `Unit` and drops the
    zip. *(Builder's call, made by Claude)*: patch the vendored YAML to add
    `schema: {type: string, format: binary}` to that response, marked with a
    `#` comment, in the same way as the existing `JdkVersionInfo.value`
    patch. If the generator still won't return the bytes, fall back to a
    small declarative `@Client("micronaut-launch")` method for this one call
    and note why in the code.
- the read-only defaults shown in the form come from `/select-options`.
  These were checked and match what `/create` applies when given no
  parameters (DEFAULT / JAVA / GRADLE_KOTLIN / JUNIT / JDK_25 at the time of
  writing).
- extraction to disk is its own small piece. It is filesystem logic, kept
  out of the UI and out of the HTTP adapter.
- the TamboUI screens are the inbound side. `MtuiCommand` starts them and
  turns the outcome into the exit code and the final printed lines.
  The placeholder `--verbose` option and `"..."` description are replaced
  with a real description. No new CLI options in this slice.
- the Launch base URL stays configurable through the existing
  `micronaut.http.services.micronaut-launch.url` property. Nothing new is
  needed.

## Testing

Follow the existing style: WireMock for the HTTP adapter, using recorded
payloads under `src/test/resources/payloads`, and Strikt assertions.
Cover at least:

- create adapter: 201 returns the zip bytes; 400 surfaces Launch's message;
  connection failure maps to an error. Record the 400 payload from a real
  Launch response.
- extraction: creates `./my-app/`; wrapper scripts are executable; a zip
  with a `../` entry is rejected and nothing is written; an existing folder
  is refused.
- name check: allowed and rejected characters; last segment becomes the
  folder name.

Screens don't need automated tests in this slice. A manual run against the
real Launch API is the check.

## Out of scope

Feature picking (slice 2), editing type, language, build, test or JDK
(slice 3), preview (slice 4), opening in an IDE (slice 5), help, themes,
remembered preferences (slice 6), and native binaries (slice 7). Also out of
scope: taking the name as a CLI argument.

## Left to the builder

- TamboUI layout, widgets, colours and key handling beyond Enter/Esc/Ctrl+C.
- How the loading screen looks.
- Whether the read-only defaults are shown by label or description.
- How screens hand control to each other, as long as later slices can add
  screens without rewriting it.
- How executable bits are preserved, e.g. Commons Compress versus restoring
  modes for known wrapper scripts. Prefer what's already on the classpath.

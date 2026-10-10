# Slice 3: Options form

Part of the [roadmap](../../roadmap.md). This is slice 3 of 7. It builds on
[slice 2](2026-10-10-feature-picker-design.md).

## Why

Launch's defaults are a Java project, but the user usually wants something
else. For example, they always use Kotlin and Gradle. Until now `mtui` could
only make projects with the defaults. The feature list also depends on the type
and language, so the Kotlin features (kapt, ksp, config4k, …) can't be found
until Kotlin can be chosen.

Options are chosen **before** features, which is how the user works.

## What it does

The flow stays the same. Only what each step does changes:

1. **Loading screen.** Only `/select-options` is fetched. Features are no
   longer fetched here, because they depend on the options chosen on the next
   screen.
2. **Name screen.** This is the slice 1 form, plus editable options. Enter
   checks the name as before, then fetches the features for the chosen type and
   language and opens the feature picker.
3. **Feature picker.** This is unchanged from slice 2, except that the header
   shows the chosen options.
4. **Generation.** `/create` is called with the chosen type, language, build,
   test framework and JDK, plus the selected features.

Nothing is remembered between runs. Remembered preferences are slice 6.

## The name screen

This was chosen from mockups ("option B"). Every choice is visible, and the
current one is marked:

```
Create a Micronaut project

╭ Name ───────────────────────────╮
│ com.example.my-app              │
╰─────────────────────────────────╯

  Type       (•) Default  ( ) CLI  ( ) Function  ( ) gRPC  ( ) Messaging
› Language   ( ) Java  ( ) Groovy  (•) Kotlin
  Build      ( ) Gradle  (•) Gradle Kotlin  ( ) Maven
  Test       (•) JUnit  ( ) Spock  ( ) Kotest
  JDK        ( ) 17  (•) 21  ( ) 25

Tab/j/k move · ←/→ h/l change · Enter choose features · Esc quit
```

- There are five rows, in this order: Type, Language, Build, Test, JDK. Each
  row shows every value Launch offers for it, in Launch's order.
- Launch's defaults are marked to begin with.
- The focused row is highlighted.
- Type uses short labels so the row fits on one line: Default, CLI, Function,
  gRPC, Messaging. Launch's labels, such as "Function Application for
  Serverless", are too long. *(Claude's call: a small map from the type's
  value to a short label. A type that isn't in the map falls back to Launch's
  label, so a new type from Launch still shows.)*
- Build, Test and JDK use Launch's labels, as the header does today.

### Keys

| Key | Where | Does |
|-----|-------|------|
| Tab / Shift+Tab | anywhere on the form | Move focus down / up through the name box and the five rows. |
| `j` / `k`, ↓ / ↑ | option row | Move focus to the next / previous row. `k` on Type goes back to the name box. *(Claude's call: ↓ from the name box goes to Type, so the arrow keys work everywhere. `j` can't do this in the name box, because there it types a letter.)* |
| ← / →, `h` / `l` | option row | Move the mark to the previous / next value. It stops at the ends and doesn't wrap. *(Claude's call)* |
| Enter | anywhere on the form | Check the name and open the feature picker, as in slice 2. If the name is invalid or the folder exists, show the error and focus the name box. *(Claude's call: focusing the name box puts the user where the fix is.)* |
| Esc | anywhere on the form | Quit, as today. |
| Ctrl+C | anywhere | Quit with a non-zero exit code and restore the terminal. |

Letter keys only act as commands on an option row. In the name box they type,
as the roadmap requires.

### Language sets its defaults

Each language in `/select-options` has its own defaults for test framework and
build (`Language.defaults`). For example, Groovy defaults to Spock, and Java and
Kotlin default to JUnit and Gradle Kotlin. **Changing the language moves Test
and Build to that language's defaults**, replacing whatever the user set. If a
language has no defaults, Test and Build are left as they are.

The user changes language first anyway, so overwriting their Test and Build
choices costs little.

## Features follow the options

- Pressing Enter on the name screen fetches
  `/application-types/{type}/features/{lang}` for the chosen type and language.
  A spinner and "Fetching features…" show while it loads, and keys are ignored.
  *(Claude's call: the message can sit under the form or replace it. The
  builder chooses.)*
- If the user goes back from the picker (Esc) and changes **type or
  language**, the next Enter fetches the features again. Selected features that
  aren't in the new list are dropped silently. The rest stay selected, in the
  order they were chosen. Search text is cleared. *(Claude's call: silently,
  because the "Selected" pane shows what's left. If the user goes back and only
  changes the name, build, test or JDK, the feature list isn't fetched again.
  The picker reopens as they left it, with its selection, search and
  highlight.)*
- **Features fail to load:** show Launch's error on the name screen, where the
  user pressed Enter, and keep everything they entered. Enter tries again.
  This replaces slice 2's "any key exits", which only made sense when the
  fetch happened at start-up. `/select-options` failing at start-up still
  exits as before.

## Feature picker header

The header shows the chosen options instead of the defaults:

```
Create a Micronaut project   Name: com.example.my-app   DEFAULT · Kotlin · Gradle Kotlin · JUnit · JDK 21
```

## Generating

- `/create/{type}/{name}` gets the chosen type, plus `lang`, `build`, `test`
  and `javaVersion` as query parameters, using each option's `value`. All five
  are always sent, even when they're the defaults. *(Claude's call: this
  avoids special cases, and Launch accepts them.)*
- Two things in the generated client need attention:
  - In the vendored OpenAPI spec, `createApp`'s `javaVersion` uses the
    `JdkVersion` *object* schema, so `JDK_21` can't be passed as it is.
    Patch the spec to make it a string or enum, the same way as the existing
    `# mtui patch` on `createApp`. Then check against real Launch which form
    it accepts (`JDK_21` or `21`).
  - `test` uses a separate enum (`TestFramework_1`, which also has
    `KOTLINTEST`). Map the chosen value through that enum. An unknown value
    fails in the same way as an unknown type does today in
    `MicronautLaunchProjectCreator`.
- Some combinations may be rejected by Launch, for example Kotest with Java.
  `mtui` doesn't check combinations itself. Launch's 400 message shows on the
  picker, as with clashing features in slice 2. The user presses Esc to go
  back and change the option.

## Structure

This follows the existing layout:

- **A domain model for the chosen options:** type, language, build, test
  framework and JDK. It is built from `SelectOptions`' defaults, with a pure
  function for "change language" that applies the language defaults (looking
  up the `TestFramework` and `BuildType` whose `value` matches). This replaces
  passing `options.defaultType` around.
- **`CatalogLoader`** splits in two: loading the select options at start-up,
  and loading features for a given type and language. `Catalog` may no longer
  be needed. If so, delete it.
- **`ProjectCreator.createProject` and `ProjectGenerator.generate`** take the
  chosen options instead of just the type. The Launch adapter passes them to
  the generated `createApp` (`lang`, `build`, `test`, `javaVersion`).
- **`FeaturePicker`** needs a way to keep a selection when it's rebuilt with a
  new feature list. For example, build it with an initial selection and drop
  names that aren't in the list. That logic is unit tested.
- **The options form state** (focused row, changing a value) is kept out of
  TamboUI, like `FeaturePicker`, so it can be unit tested. The rendering lives
  in `MtuiApp` or a new `OptionsForm` screen, whichever the builder finds
  cleaner.

## Testing

Follow the existing style: WireMock with recorded payloads, and Strikt. Cover
at least:

- create adapter: `lang`, `build`, `test` and `javaVersion` are sent as query
  parameters. Record a 400 from real Launch for an invalid combination, if one
  exists, and check its message comes through.
- chosen options: start from Launch's defaults; changing language applies its
  test and build defaults; a language with no defaults leaves them alone;
  moving the mark stops at the ends.
- feature picker: rebuilding with a new list keeps the selected features that
  still exist, in order, and drops the rest.
- catalog loader / generator: features are fetched for the given type and
  language, and the chosen options reach the creator.

Screens still don't need automated tests. A manual run against real Launch is
the check: choose Kotlin, search for "kotlin" in the picker, select kapt,
generate, and check the project's `build.gradle.kts` uses Kotlin and kapt.
Then go back, switch to Java, and check that kapt has gone from the selection
if Java's list doesn't have it.

## Out of scope

Remembering choices between runs (slice 6). Checking option combinations
before calling Launch. Picking the Micronaut version. Preview (slice 4).

## Left to the builder

- TamboUI widgets, colours and how the focused row and marked value look.
- What happens in a terminal too narrow for the Type row. Truncating or
  wrapping is fine.
- Whether `0` / `$` or Home / End jump to the first / last value.
- Whether the name box is focused when the form opens. It is today, and that
  is a sensible default.

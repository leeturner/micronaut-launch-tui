# Slice 2: Feature picker

Part of the [roadmap](../../roadmap.md). This is slice 2 of 7. It builds on
[slice 1](2026-10-10-walking-skeleton-design.md).

## Why

Finding the right features is the most annoying part of creating a Micronaut
project, and the reason `mtui` exists. Launch offers about 320 features in
38 categories. The usual problem is knowing roughly what you want ("something
for Kotlin") but not the feature's name. Searching for "kotlin" should narrow
the list to kapt, ksp, config4k, kotlin-extension-functions and so on, even
though none of those names starts with "kotlin".

## What it does

The flow becomes two steps:

1. **Loading screen.** As in slice 1, `/select-options` is fetched. Then the
   features for the default type and language are fetched from
   `/application-types/{type}/features/{lang}` behind the same loading
   screen. This call needs the defaults from the first one, so they run in
   sequence.
2. **Name screen.** This is the slice 1 form, unchanged except that Enter
   now opens the feature picker instead of generating. Enter still checks
   the name format and refuses a folder that already exists, as in slice 1,
   before moving on. Today that folder check only runs inside
   `ProjectGenerator.generate`, so the name screen needs a way to call it.
   Keep the second check in `generate` too, because the folder could be
   created while the user is in the picker.
3. **Feature picker.** You pick features, then Enter generates the project.
4. Generation, extraction and the final printed lines are as in slice 1,
   except that `/create` is now called with the selected features.

## The feature picker screen

This was chosen from mockups ("option B"):

```
Create a Micronaut project   Name: com.example.my-app   DEFAULT · Java · Gradle Kotlin · JUnit · JDK 25
╭ Features  / kotlin ─────────────────────────────╮╭ Selected (2) ────────────╮
│ Development Tools                               ││ kapt                     │
│ [x] kapt          Kotlin annotation processing  ││ kotlin-extension-functions│
│ [ ] ksp           Kotlin Symbol Processing      ││                          │
│ Configuration                                   ││                          │
│ [ ] config4k      Kotlin config with config4k   ││                          │
│ …                                               ││                          │
╰─────────────────────────────────────────────────╯╰──────────────────────────╯
 Kotlin annotation processing (kapt): description of the highlighted feature
 j/k move · Space toggle · / search · x clear · Enter generate · Esc back
```

- **One list** of every feature, grouped under category headings. Each row
  shows a checkbox, the feature name and its title.
- **A "Selected" pane** on the right always lists the selected features, with
  a count, whatever the list is showing.
- **A description line** under the list shows the highlighted feature's
  description.
- **Preview and community features** get a short tag on their row (e.g.
  `preview`, `community`). Launch currently has 18 preview features and 1
  community feature.
- **A header** shows the project name and the defaults it will use, so the
  user doesn't lose track of them.

### Keys

| Key | Where | Does |
|-----|-------|------|
| `j` / `k`, ↓ / ↑ | list | Move the highlight. Category headings are skipped. |
| Space | list | Toggle the highlighted feature. |
| `/` | list | Move focus into the search box. |
| typing | search box | Filter the list live. |
| Enter or Esc | search box | Return focus to the list and keep the filter. |
| `x` | list | Clear all selected features, straight away with no confirmation. |
| Enter | list | Generate the project with the selected features. Selecting nothing is allowed and gives Launch's defaults, as in slice 1. |
| Esc | list | Clear the filter if there is one. Otherwise go back to the name screen and keep the selection. |
| Ctrl+C | anywhere | Quit with a non-zero exit code and restore the terminal. |

### Search

- A case-insensitive substring match against the feature's name, title and
  description. No typo tolerance in this slice.
- Categories with no matching features are hidden.
- An empty search shows everything.
- Selected features stay selected when the filter hides them. The "Selected"
  pane still shows them.
- If the highlighted feature is filtered out, the highlight moves to the first
  visible feature. If nothing matches, the list says so and Space does
  nothing.

## Vim keys across the app

This applies to every slice from now on, and the roadmap records it: every
list moves with the arrow keys **and** `j`/`k`. `/` starts a search wherever a
list can be searched, as in vim. Letter keys only act as commands when focus
is on a list, never while a text field has focus.

## Behaviour that matters

- **Features fail to load:** treat it the same as `/select-options` failing in
  slice 1. Show the error on the loading screen, and any key exits non-zero.
- **`/create` returns 400:** this can now be a name problem (`Invalid package
  name: MyApp`) or a feature problem. Launch rejects features that clash, e.g.
  `There can only be one of the following features selected: [data-jdbc,
  data-jpa]`. Show Launch's message on the picker, where the user pressed
  Enter, and keep the selection. If it's a name problem, the user presses Esc
  to go back and fix it. *(Claude's call: Launch's messages are readable on
  their own, and telling the two kinds apart would mean parsing message
  text.)*
- **Other `/create` failures:** show the error on the picker and let the user
  try again.
- **Generating:** as in slice 1, keys are ignored while the project is being
  generated and written.
- **Order of selected features:** the "Selected" pane lists them in the order
  they were selected. The order sent to Launch doesn't matter.

## Structure

This follows the slice 1 layout:

- a new domain model for a feature: name, title, description, category,
  preview, community.
- a new outbound port for retrieving features for a type and language,
  returning Arrow `Either` with a typed error, like `SelectOptionRetriever`.
- a Launch HTTP adapter for it using the generated
  `MicronautLaunchDefaultApi.featuresByLanguage`, mapping errors in the same
  way as `MicronautLaunchSelectOptionRetriever`.
- `ProjectCreator.createProject` and `ProjectGenerator.generate` take the
  selected feature names, and the Launch adapter passes them as the
  `features` query parameter of `/create`.
- the search filter and grouping are plain functions over the feature list,
  kept out of the TamboUI code so they can be unit tested.
- the picker is a new screen in `MtuiApp`, alongside the existing ones.

## Testing

Follow the existing style: WireMock with recorded payloads under
`src/test/resources/payloads`, and Strikt. Cover at least:

- features adapter: 200 maps the features (record the payload from real
  Launch); a failure status and a connection failure map to errors.
- create adapter: the selected features are sent as the `features` query
  parameter; a 400 for clashing features surfaces Launch's message (record
  it from real Launch).
- search: matches on name, title and description; is case-insensitive; empty
  categories are hidden; an empty search returns everything.
- generator: features are passed through to the creator.

Screens still don't need automated tests. A manual run against real Launch is
the check. With Launch's default language (Java), a search for "postgres"
or "jdbc" is a good test. The "kotlin" case only becomes useful once slice 3
lets the user pick Kotlin, because Java's feature list has almost no Kotlin
features.

## Out of scope

Typo-tolerant search, changing type or language (slice 3), preview (slice 4),
remembered or recently used features (slice 6), and warning about clashing
features before calling Launch.

## Left to the builder

- TamboUI widgets, colours, pane widths, and how the search box looks when
  focused or unfocused.
- How long rows are truncated in a narrow terminal.
- The order of categories and of features within a category. Alphabetical is
  fine.
- Whether `gg` / `G`, Ctrl+D / Ctrl+U or page keys are supported. They're nice
  to have but not required.
- Whether the "Selected" pane can be focused. It only has to display the
  selection in this slice.

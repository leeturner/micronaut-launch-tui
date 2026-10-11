# Slice 4: Micronaut version

Part of the [roadmap](../../roadmap.md). This is slice 4 of 8. It builds on
[slice 3](2026-10-10-options-form-design.md).

## Why

`mtui` only talks to `launch.micronaut.io`, so it can only make projects on
the current Micronaut release. The user sometimes wants to try something out on
the next snapshot. Micronaut runs a second Launch server for that at
`snapshot.micronaut.io`.

The user has only ever used the latest release or the snapshot, never the
previous major version. So `prev.micronaut.io` (4.x) is **dropped** from this
slice and from the roadmap row. If anyone else asks for it in an issue or a PR,
it can be added later. Dropping it also avoids its one quirk: it has no
per-language feature list (`/application-types/{type}/features/{lang}` returns
404), so features would need fetching differently.

## What it does

There are two Launch servers to choose from:

| Server | URL | Label (today) |
|--------|-----|---------------|
| Release | `https://launch.micronaut.io` | 5.2.2 |
| Snapshot | `https://snapshot.micronaut.io` | 5.2.3-SNAPSHOT |

- The version is chosen on the name screen. The release is chosen to begin
  with.
- Every Launch call goes to the chosen server: select options, features, and
  creating the project.
- Each server is labelled with its Micronaut version, taken from the
  `micronaut.version` entry of that server's `/versions` response (`{"versions":
  {"micronaut.version": "5.2.2", …}}`).

Nothing is remembered between runs. Remembered preferences are slice 7.

## The name screen

This was chosen from mockups ("option A"). The version is a new row **above
Type**. It works like the other option rows, and it comes first because the rows
below it come from whichever server it picks.

```
Create a Micronaut project

╭ Name ───────────────────────────╮
│ com.example.my-app              │
╰─────────────────────────────────╯

› Version    ( ) 5.2.2  (•) 5.2.3-SNAPSHOT
  Type       (•) Default  ( ) CLI  ( ) Function  ( ) gRPC  ( ) Messaging
  Language   ( ) Java  ( ) Groovy  (•) Kotlin
  Build      ( ) Gradle  (•) Gradle Kotlin  ( ) Maven
  Test       (•) JUnit  ( ) Spock  ( ) Kotest
  JDK        ( ) 17  (•) 21  ( ) 25

Tab/j/k move · ←/→ h/l change · Enter choose features · Esc quit
```

- The keys are the same as slice 3's. Version is now the first row, so `k` on
  Version goes back to the name box, and ↓ from the name box goes to Version.
- Release comes first, then snapshot. Moving the mark stops at the ends and
  doesn't wrap, as on the other rows.

## At start-up

*(Claude's call, agreed with the user.)*

- The loading screen fetches `/select-options` from the release server, as it
  does today. At the same time, it fetches `/versions` from **both** servers to
  get the labels.
- If a `/versions` call fails, or its response has no `micronaut.version`,
  start-up carries on and that server is labelled "Release" or "Snapshot". A
  missing label is never a reason to stop.
- `/select-options` failing still exits, as before.
- The loading screen doesn't wait for `/versions` any longer than it waits for
  `/select-options`. *(Claude's call: the builder can run them in parallel or
  one after another, as long as a slow `/versions` doesn't keep the user
  waiting noticeably. A short timeout on `/versions` is fine.)*

## Switching version

*(Claude's call, agreed with the user.)*

- Moving the mark on the Version row switches straight away. `/select-options`
  is fetched from the newly chosen server. While it loads, the rows are dimmed
  and a spinner shows "Loading 5.2.3-SNAPSHOT options…", using the server's
  label. *(Claude's call: the label is already in the domain model, so the TUI
  never needs a host name.)* Keys are ignored while it loads, except Ctrl+C, which
  still quits.
- When the options arrive, **each choice is kept if the new server offers it**,
  matched by `value`: type, language, build, test framework and JDK. A choice
  the new server doesn't offer falls back to that server's default. This
  doesn't apply the language defaults (Test and Build): the user's choices are
  carried over as they are.
- **If the load fails**, Launch's error shows on the name screen and the mark
  goes back to the version the user was on. Everything they entered is kept,
  including the options and the name. The rows still show the previous server's
  options.
- Pressing ←/→ past the end, so the version doesn't change, does nothing. It
  doesn't reload.

## Features and generating

- A version change counts like a type or language change in slice 3. The next
  Enter fetches the features again, from the chosen server. Selected features
  that still exist stay selected, in the order they were chosen. The rest are
  dropped silently, and search text is cleared. This reuses slice 3's
  `FeaturePicker` behaviour.
- `/create/{type}/{name}` goes to the chosen server.
- The feature picker header shows the version first:

  ```
  Create a Micronaut project   Name: com.example.my-app   5.2.3-SNAPSHOT · DEFAULT · Kotlin · Gradle Kotlin · JUnit · JDK 21
  ```

- Errors from the snapshot server (failed feature fetch, 400 on create) are
  shown exactly as they are for the release server today.
- The generated client comes from the release's OpenAPI spec. If the snapshot
  ever offers a type, language, build or test framework that the spec doesn't
  know about, loading or generating fails with an error. That is fine: it's the
  failed-load case above. *(Claude's call: both servers offer the same values
  today, so there is no need to read the snapshot's own spec or drop unknown
  values.)*

## Structure

This follows the existing hexagonal layout.

- **A domain model for a Launch server:** which server it is (release or
  snapshot), and its label. The two servers are fixed. They don't need to be
  configurable. *(Claude's call: the URLs can live in `application.properties`
  next to today's `micronaut.http.services.micronaut-launch.url`, or in code,
  whichever is simpler with the client approach below.)*
- **The chosen server is part of `ProjectOptions`**, so it travels with the
  other choices to the feature fetch and to `createProject`. *(Claude's call:
  the builder may pass it separately instead if that reads better. Either way,
  `CatalogLoader.loadOptions`, `CatalogLoader.loadFeatures` and
  `ProjectCreator.createProject` all need to know which server to call.)*
- **A new outbound port and adapter for the version label**, for example a
  `VersionRetriever` that returns the Micronaut version for a server, using the
  generated `getInfo` (`/versions`) operation.
- **Carrying choices over to a new server's options** is a pure function next
  to `withLanguage` in `ProjectOptions.kt`, unit tested.
- **`OptionsForm`** gets a `VERSION` row first in `OptionRow`. Changing it
  doesn't change `chosen` by itself. The screen loads the new options, then
  rebuilds the form with the carried-over choices. *(Claude's call: the
  builder decides how `OptionsForm` is rebuilt or re-pointed at the new
  `SelectOptions`.)*

### Calling two servers with the generated client

The generated `MicronautLaunchDefaultApi` is a declarative client bound to one
service id (`micronaut-launch`), and so to one URL. Calling a second server is
the main technical decision in this slice, and it is **left to the builder**.
Some options:

- A second `client(...)` block in `build.gradle.kts` from the same spec, with
  its own `clientId` (`micronaut-launch-snapshot`) and package. The adapters
  then get both APIs and pick one by server. This is simple, but it doubles the
  generated code.
- An `HttpClientFilter` on the `micronaut-launch` client that rewrites the host
  to the chosen server. This avoids duplicate code, but it puts hidden state
  in a filter.
- Something else the builder finds cleaner, such as building the client at
  runtime per server.

Whatever is chosen, the domain and the TUI must not know about clients or URLs.
They only know which server is chosen.

## Testing

Follow the existing style: WireMock with recorded payloads, and Strikt. Cover
at least:

- version adapter: reads `micronaut.version` from a recorded `/versions`
  payload. A failure, or a response without it, comes back as an error, not an
  exception.
- server choice: the select options, features and create adapters call the
  snapshot server when it is chosen, and the release server otherwise. For
  example, use two WireMock servers, or one with different paths if the client
  approach allows it.
- carrying choices over: a choice the new options still offer is kept; one
  they don't offer falls back to the new default; the language defaults are not
  applied.
- options form: the Version row comes first, and moving its mark stops at the
  ends.

Screens still don't need automated tests. A manual run against real Launch is
the check:

1. Start `mtui`. Check that the Version row shows the real release and snapshot
   versions.
2. Choose Kotlin, then switch to the snapshot. Check that Kotlin is still
   chosen.
3. Pick a feature and generate. Check that the project's
   `gradle.properties` has the snapshot's `micronautVersion`.
4. Switch back to the release and check the same thing.

## Roadmap change

Update slice 4's row in `docs/roadmap.md` to match: release and snapshot only,
with no `prev`. Its Launch API column becomes `/versions`. Mark it done when
the slice merges, as with the earlier slices.

## Out of scope

- `prev.micronaut.io` and any other Launch server.
- Typing in a custom Launch URL.
- Remembering the chosen version between runs (slice 7).
- Preview (slice 5).

## Left to the builder

- How the two servers are called with the generated client (see above).
- TamboUI details for the dimmed rows and the spinner while options load.
- Whether `/versions` and `/select-options` run in parallel at start-up.

# Slice 7: Help and README

Part of the [roadmap](../../roadmap.md). This is slice 7 of 8. It builds on
[slice 6](2026-10-11-open-with-command-design.md).

## Why

The roadmap had slice 7 as "Polish": a help overlay, themes, remembered
preferences and recently used features. The user felt a settings file was too
much at this stage, and three of those four need one. `MTUI_OPEN` already
covers the one per-user preference that saves real time, and Launch's defaults
are good. So themes, remembered preferences and recently used features move to
"possible later" on the roadmap.

What's left is the help overlay, plus a README. The README is still the
Micronaut CLI template's boilerplate, so someone landing on the repo can't tell
what `mtui` is.

## Part 1: Help overlay

Every screen already ends with a one-line hint (for example
`j/k move · Space toggle · / search · x clear · p preview · Enter generate · Esc back`).
The help overlay is the fuller version: every key on the current screen, each
with a few words on what it does.

### What it looks like

The user chose a popup (option A of three mockups):

- A bordered box in the middle of the screen, titled with the screen's name
  (e.g. "Feature picker keys").
- One key per line: the key, then a short explanation. For example
  `Esc  clear the search, then go back`.
- The last line is a dim `? or Esc to close`.
- The screen underneath stays where it was, drawn dimmed behind the box.

### Which screens

| Screen | Help shown |
|--------|------------|
| Name and options form | form keys |
| Feature picker | picker keys |
| Preview, reading a file | preview keys |
| Preview, file list open | file list keys |
| Loading, generating, load failed | none: `?` does nothing |

Each list covers every key the screen handles, including the ones the hint line
leaves out, like `Ctrl+C` to quit, `↑/↓` alongside `j/k`, and that `Esc` on the
picker clears the search before going back. Read the screens' `handleKey`
functions for the full set.

### Keys

- `?` opens the help for the current screen.
- **In the name box**, `?` still opens help. A project name can't contain `?`
  (see `ProjectName`), and the name box has focus when the app starts, which is
  exactly when someone is most likely to press it.
- **In the preview's file list**, `?` also opens help. Every key typed there goes
  into the filter (there's no separate box to leave), and generated file paths
  never contain `?`, so nothing is lost. `PreviewScreen.handleListKey` checks
  for `?` before passing the key to the filter.
- **In the picker's search box**, `?` is typed as a character, as now. Someone
  may want to search for it.
- While help is open, `?` or `Esc` closes it and `Ctrl+C` quits. Every other key
  is ignored, so nothing changes behind the popup.
- Closing returns to the screen exactly as it was, focus included.
- Each screen's hint line gains `? help` at the end.

### How

- Each screen's help list lives next to its hint line, so a key change touches
  both in one place.
- No new port or adapter. This is all inside the TUI adapter.
- How the popup is drawn (a TamboUI overlay or stack, or building the dimmed
  screen plus box by hand) is left to the builder. Check what TamboUI offers
  first.

## Part 2: README

Rewrite `README.md` from scratch for someone who lands on the repo and wants to
know what `mtui` is, what it looks like and how to run it. The user pointed at
[spring-initializr-tui's README](https://github.com/danvega/spring-initializr-tui)
as inspiration.

In order:

1. **Title and one line**: a terminal UI for creating Micronaut projects through
   [Micronaut Launch](https://launch.micronaut.io), built with
   [TamboUI](https://tamboui.dev).
2. **Animated GIF** of a whole run: type a name, search for a feature, select
   it, generate.
3. **Why**: a sentence or two about the problem: there are too many Micronaut
   features to find the right ones on the web UI. Credit
   spring-initializr-tui as the inspiration.
4. **Features**: a short bullet list of what it does today (search, options,
   version switching between release and snapshot, preview, open with your
   command, help).
5. **Screenshots**: one still each of the options form, the feature picker, the
   file preview and the help popup, with a line under each saying what you can
   do there.
6. **Requirements, build and run**: Java 25, `./gradlew shadowJar`,
   `java -jar build/libs/<the shadow jar>`. There are no prebuilt downloads yet;
   slice 8 will rewrite this section.
7. **Shell access**: a suggested alias so `mtui` works from any directory, and
   the `mkdir my-app && cd my-app && mtui` style of usage (the project unzips
   into the current directory).
8. **Opening the project**: `--open` and `MTUI_OPEN`, with an `idea` example.
9. **Keys**: one line saying every list moves with arrows or `j/k`, `/`
   searches, and `?` shows all keys on any screen. No full key table: it would
   drift and the app already shows the keys.
10. **Development**: build, test and lint commands, links to the roadmap and the
    specs, and how to regenerate the screenshots.

The template boilerplate (Kotlinter, Detekt, Arrow feature list, "How to use
this template", Micronaut doc links) goes.

### Screenshots with vhs

The screenshots are made with [vhs](https://github.com/charmbracelet/vhs)
(installed on the user's machine with `ttyd` and `ffmpeg`), so they can be
regenerated whenever the UI changes.

- vhs scripts (`.tape` files) live in `docs/tapes/`, images in `docs/images/`.
  Both are committed.
- One tape for the GIF and one per still. The tapes build or run the shadow jar.
- They talk to the real Launch server, so what they show depends on what Launch
  offers that day. That's accepted.
- The README's Development section says how to regenerate them.
- The tape that generates a project must do it in a throwaway directory, so it
  never leaves a project behind in the repo.

## Calls made in this spec

The user agreed to all of these in conversation, but they were Claude's
suggestions:

- `?` opens help from the name box and the file list (user chose this when the
  reviewer spotted the file list has no separate filter box), but types into
  the picker search.
- Keys other than `?`, `Esc` and `Ctrl+C` are ignored while help is open.
- No key table in the README.
- Tapes in `docs/tapes/`, images in `docs/images/`.

## Not in this slice

- Themes, remembered preferences, recently used features, or any settings file.
  These move to "possible later".
- Help on the loading, generating and load-failed screens.
- A licence or contributing section. The repo has no licence file; adding one is
  the user's call.
- Prebuilt binaries and install instructions for them (slice 8).

## Done when

- `?` on the form (including from the name box), the picker, the preview and the
  preview's file list opens a popup listing that screen's keys. `?` or `Esc`
  closes it and leaves the screen as it was.
- `?` typed in the picker's search goes into the box.
- Every hint line ends with `? help`.
- Tests cover opening and closing help on each screen, `?` in the name box and
  file list versus the search box, and other keys being ignored while help is
  open.
- `README.md` is rewritten as above, with the GIF and four stills generated by
  the committed vhs tapes.
- The roadmap shows slice 7 as "Help and README", and themes, remembered
  preferences and recently used features are listed under "possible later".

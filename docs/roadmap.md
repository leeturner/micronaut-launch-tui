# Roadmap

`mtui` is a terminal UI for creating Micronaut projects via the
[Micronaut Launch](https://launch.micronaut.io) API, inspired by
[spring-initializr-tui](https://github.com/danvega/spring-initializr-tui).

The most annoying part of creating a Micronaut project is **finding the right
features: there are too many**. Everything here is ordered to get to a good
feature picker quickly.

The work is split into vertical slices. Each slice gets its own spec in
`docs/superpowers/specs/`, then a plan, then is built and merged before the
next slice's spec is written.

Across every slice: every list moves with the arrow keys **and** vim's `j`/`k`,
and `/` starts a search wherever a list can be searched.

| # | Slice | Status | Done when | Launch API |
|---|-------|--------|-----------|------------|
| 1 | Walking skeleton | Done | `mtui` loads, asks for name and package, generates the project with Launch defaults and unzips it into the current directory | `/select-options`, `/create/{type}/{name}` |
| 2 | Feature picker | Done | Features, grouped by category, can be found by search (substring of name, title or description), toggled, and the selection is always visible | `/application-types/{type}/features/{lang}` |
| 3 | Options form | Done | Type, language, build, test framework and JDK are editable; changing type or language refreshes the available features | `/select-options` |
| 4 | Micronaut version | Done | The version is chosen on the name screen from the release and snapshot Launch servers (`launch` and `snapshot.micronaut.io`, each labelled from `/versions`); changing it reloads the options and features from that server | `/versions` |
| 5 | Preview | Done | Generated files can be browsed before creating the project, from the name screen or the feature picker; ArchUnit tests guard the ports and adapters boundaries | `/preview/{type}/{name}` |
| 6 | Open with your command | Done | After creating, the project is opened with a command set by `--open` or `MTUI_OPEN` (e.g. `idea`); with neither set nothing changes | — |
| 7 | Help and README | Done | `?` shows the current screen's keys in a popup; the README shows what `mtui` is, with screenshots made by committed vhs tapes | — |
| 8 | Distribution | | Native binaries and a release workflow | — |

Possible later: feature diff (`/diff/{type}/feature/{feature}`), showing what
adding a feature changes. Spring Initializr has no equivalent.

Also possible later, dropped from slice 7 because they need a settings file:
themes, remembered preferences and recently used features.

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
| 2 | Feature picker | | Features, grouped by category, can be found by search (substring of name, title or description), toggled, and the selection is always visible | `/application-types/{type}/features/{lang}` |
| 3 | Options form | | Type, language, build, test framework and JDK are editable; changing type or language refreshes the available features | `/select-options` |
| 4 | Preview | | Generated files can be browsed before creating the project | `/preview/{type}/{name}` |
| 5 | After generating | | Open the project in an IDE or terminal, generate another, or quit | — |
| 6 | Polish | | Help overlay, themes, remembered preferences, recently used features | — |
| 7 | Distribution | | Native binaries and a release workflow | — |

Possible later: feature diff (`/diff/{type}/feature/{feature}`), showing what
adding a feature changes. Spring Initializr has no equivalent.

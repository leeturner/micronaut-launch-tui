# mtui

[![Compile and Build](https://github.com/leeturner/micronaut-launch-tui/actions/workflows/build.yml/badge.svg)](https://github.com/leeturner/micronaut-launch-tui/actions/workflows/build.yml)

A terminal UI for creating Micronaut projects through
[Micronaut Launch](https://launch.micronaut.io), built with
[TamboUI](https://tamboui.dev).

![Creating a project with mtui](docs/images/demo.gif)

## Why

The hardest part of creating a Micronaut project is finding the right features:
there are hundreds of them. `mtui` puts them in one searchable list, so you
type a few letters, press Space and move on, without leaving the terminal or
unzipping a download.

It was inspired by Dan Vega's
[spring-initializr-tui](https://github.com/danvega/spring-initializr-tui), which
does the same for Spring Boot.

## Features

- Search every feature by name, title or description, and see what you have
  selected at all times
- Choose the application type, language, build tool, test framework and JDK
- Switch between the latest Micronaut release and the current snapshot
- Preview the generated files before creating anything
- Unzip the project straight into the current directory
- Open the project with your editor or IDE once it is created
- Press `?` on any screen to see its keys

## Screenshots

### Name and options

Type the project name, then pick the Micronaut version, application type,
language, build tool, test framework and JDK.

![The name and options screen](docs/images/form.png)

### Features

Press `/` to search, Space to select. The features you have chosen stay
listed on the right.

![The feature picker](docs/images/picker.png)

### Preview

Press `p` to browse the files Launch would generate, with `f` to jump to a
file by name.

![The file preview](docs/images/preview.png)

### Help

Press `?` on any screen for every key it understands.

![The help popup](docs/images/help.png)

## Build and run

You need Java 25. There are no prebuilt downloads yet.

```bash
./gradlew shadowJar
java -jar build/libs/micronaut-launch-tui-0.1-all.jar
```

### Run it from anywhere

Add an alias to `~/.zshrc` or `~/.bashrc`:

```bash
alias mtui='java -jar /path/to/micronaut-launch-tui/build/libs/micronaut-launch-tui-0.1-all.jar'
```

Then create projects from wherever you keep them. The project is unzipped into
the current directory:

```bash
cd ~/dev
mtui
```

### Open the project when it's created

Give `mtui` the command that opens a project, and it runs it with the new
project's path once it's created:

```bash
mtui --open idea
```

Or set it once in your shell profile. `--open` wins if both are set:

```bash
export MTUI_OPEN=idea
```

## Keys

Every list moves with the arrow keys or `j`/`k`, `/` starts a search, and `?`
shows all of the current screen's keys.

## Development

```bash
./gradlew build         # tests, Kotlinter and Detekt
./gradlew formatKotlin  # fix formatting
```

The work is planned in slices on the [roadmap](docs/roadmap.md), each with its
own design in [`docs/superpowers/specs`](docs/superpowers/specs).

### Screenshots

The images in `docs/images` are made with [vhs](https://github.com/charmbracelet/vhs)
from the tapes in `docs/tapes`. They use the live Launch server, so they show
whatever it offers on the day. To regenerate them, from the repo root:

```bash
./gradlew shadowJar
for tape in docs/tapes/{demo,form,picker,preview,help}.tape; do vhs "$tape"; done
```

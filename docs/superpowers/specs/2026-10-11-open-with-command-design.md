# Slice 6: Open with your command

Part of the [roadmap](../../roadmap.md). This is slice 6 of 8. It builds on
[slice 5](2026-10-11-preview-design.md).

## Why

The roadmap first had slice 6 as "After generating": open the project in an
IDE or a terminal, generate another, or quit. The user said that matters less
to them. Most of what they find annoying about the Launch web UI (moving the
download out of `Downloads` and unzipping it) is already gone, because `mtui`
unzips into the current directory. The only step left is opening the project
in their IDE (IntelliJ IDEA).

Working out which IDEs are installed, or hard-coding IntelliJ, would suit some
people and not others. So instead the user tells `mtui` what command opens a
project, and `mtui` runs it.

## What it does

- A new option, `--open <command>`, sets the command. Without it, the
  `MTUI_OPEN` environment variable is used. `mtui --help` shows both.
- After the project is created, `mtui` prints the path and the `cd` line, the
  same as now. Then, **if a command is set**, it runs that command with the
  project's absolute path added as the last argument, without asking. For
  example, `MTUI_OPEN=idea` runs `idea /path/to/demo`.
- With no command set, nothing changes from today.
- If the command fails, `mtui` prints this to stderr and exits with 1:
  `Could not open the project with "<command>": <reason>`. Failing means it
  can't be found, it can't start, or it exits with a non-zero code. The project
  has already been created and stays where it is.
- Otherwise `mtui` exits with 0, as it does now.

Only a successful create runs the command. When the user cancels or loading
fails, it doesn't run.

## How

- The command is split on whitespace into the program and its arguments, so
  `code -n` works. Arguments with quotes or spaces inside them aren't supported.
  An empty or blank value counts as not set.
- The command runs **after the TUI has closed**, from the picocli command class
  (`MtuiCommand`, the composition root). It never clashes with the TUI's
  screen. It inherits the terminal (stdin, stdout and stderr), and `mtui` waits
  for it to finish. A terminal editor like `nvim` works. `idea` and `code` hand
  the project off to the IDE and return straight away.
- No new port, adapter or interface. Opening is a small step after the TUI
  exits, not part of the domain.

## Calls made in this spec

These are Claude's calls. The user can change them:

- Splitting on whitespace instead of parsing shell quoting.
- Inheriting the terminal and waiting for the command to finish.
- Exiting with 1 when the open fails, even though the project was created.
- The exact wording of the error message.

## Not in this slice

- Detecting installed IDEs, or any built-in IDE list.
- Opening from inside the TUI, a "done" screen, "generate another".
- A settings file. Remembering the command belongs with slice 7's remembered
  preferences, if it's wanted there.
- Windows quirks. For example, `code` on Windows is `code.cmd`, which
  `ProcessBuilder` won't start without `cmd /c`. Slice 8 (distribution) can
  pick this up.

## Done when

- `MTUI_OPEN=idea mtui`, after creating `demo`, opens `demo` in IntelliJ.
- `mtui --open "code -n"` overrides `MTUI_OPEN`.
- With neither set, the output and exit code are the same as before this slice.
- A command that doesn't exist prints the error, exits with 1, and leaves the
  project on disk.
- Tests cover running the command with the path, splitting on whitespace, the
  option overriding the environment variable, blank meaning not set, and the
  failure message and exit code.

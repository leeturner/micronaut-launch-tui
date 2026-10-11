# Shared by every tape: run from the repo root after ./gradlew shadowJar
Set FontSize 16
Set Width 1400
Set Height 800
Set Padding 20
Set TypingSpeed 60ms
Set Theme "Catppuccin Mocha"

# mtui runs in a fresh temp directory, so a generated project never lands in the repo
Hide
Type `alias mtui="java -jar $PWD/build/libs/micronaut-launch-tui-0.1-all.jar" && cd "$(mktemp -d)" && clear`
Enter
Show

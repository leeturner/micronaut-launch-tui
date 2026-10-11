package com.leeturner.mtui.adapters.inbound.tui

import com.leeturner.mtui.domain.core.model.ProjectFile
import com.leeturner.mtui.domain.core.model.ProjectPreview
import org.junit.jupiter.api.Test
import strikt.api.expectThat
import strikt.assertions.isEqualTo
import strikt.assertions.isFalse
import strikt.assertions.isNull
import strikt.assertions.isTrue

class FileBrowserTest {
    private val preview =
        ProjectPreview(
            listOf(
                ProjectFile(".gitignore", "build/\n"),
                ProjectFile("README.md", "# App\n"),
                ProjectFile("build.gradle.kts", (1..12).joinToString("\n") { "line $it" }),
                ProjectFile("gradle.properties", "a=b\n"),
                ProjectFile("gradlew.bat", "@echo off\r\nset X=1\r\n"),
                ProjectFile("src/main/resources/application.properties", "x=y\n"),
                ProjectFile("empty.txt", ""),
                ProjectFile("long.txt", "a\n${"x".repeat(25)}\n\tb"),
            ),
        )
    private val browser = FileBrowser(preview)

    private fun open(path: String) {
        browser.openList()
        browser.filter = path
        browser.openHighlighted()
    }

    @Test
    fun `opens on the build file`() {
        expectThat(browser.file.path).isEqualTo("build.gradle.kts")
        expectThat(browser.position).isEqualTo(3)
    }

    @Test
    fun `next and previous stop at the ends`() {
        repeat(browser.files.size) { browser.next() }
        expectThat(browser.position).isEqualTo(browser.files.size)
        browser.scrollBy(5, 1)
        val scrolled = browser.top
        browser.next()
        expectThat(browser.position).isEqualTo(browser.files.size)
        expectThat(browser.top).isEqualTo(scrolled)
        repeat(browser.files.size) { browser.previous() }
        expectThat(browser.position).isEqualTo(1)
        browser.scrollBy(5, 1)
        val scrolledFirst = browser.top
        browser.previous()
        expectThat(browser.position).isEqualTo(1)
        expectThat(browser.top).isEqualTo(scrolledFirst)
    }

    @Test
    fun `changing file scrolls back to the top`() {
        browser.scrollBy(5, 10)
        browser.next()
        expectThat(browser.top).isEqualTo(0)
        browser.previous()
        browser.scrollBy(5, 10)
        browser.previous()
        expectThat(browser.top).isEqualTo(0)
    }

    @Test
    fun `scrolling stays within the file`() {
        browser.scrollBy(-5, 10)
        expectThat(browser.top).isEqualTo(0)
        browser.scrollBy(100, 10)
        expectThat(browser.top).isEqualTo(2)
    }

    @Test
    fun `an empty or short file never scrolls`() {
        open("empty.txt")
        browser.scrollBy(1, 10)
        expectThat(browser.top).isEqualTo(0)
    }

    @Test
    fun `CRLF lines have no carriage returns`() {
        open("gradlew.bat")
        expectThat(browser.lines).isEqualTo(listOf("@echo off", "set X=1", ""))
    }

    @Test
    fun `the list filters by path ignoring case`() {
        browser.openList()
        browser.filter = "PROP"
        expectThat(browser.matches.map { it.path })
            .isEqualTo(listOf("gradle.properties", "src/main/resources/application.properties"))
    }

    @Test
    fun `opening the list highlights the current file`() {
        browser.openList()
        expectThat(browser.listOpen).isTrue()
        expectThat(browser.highlighted?.path).isEqualTo("build.gradle.kts")
    }

    @Test
    fun `the highlight follows the filter`() {
        open("README.md")
        browser.openList()
        expectThat(browser.highlighted?.path).isEqualTo("README.md")
        browser.filter = "prop"
        expectThat(browser.highlighted?.path).isEqualTo("gradle.properties")
    }

    @Test
    fun `enter on a filtered list opens that file and closes the list`() {
        browser.scrollBy(5, 10)
        browser.openList()
        browser.filter = "readme"
        browser.openHighlighted()
        expectThat(browser.file.path).isEqualTo("README.md")
        expectThat(browser.top).isEqualTo(0)
        expectThat(browser.listOpen).isFalse()
    }

    @Test
    fun `enter with no matches does nothing`() {
        browser.openList()
        browser.filter = "zzz"
        expectThat(browser.highlighted).isNull()
        browser.openHighlighted()
        expectThat(browser.file.path).isEqualTo("build.gradle.kts")
        expectThat(browser.listOpen).isTrue()
    }

    @Test
    fun `closing the list keeps the file`() {
        browser.openList()
        browser.moveHighlight(1)
        browser.closeList()
        expectThat(browser.listOpen).isFalse()
        expectThat(browser.file.path).isEqualTo("build.gradle.kts")
    }

    @Test
    fun `tabs become spaces`() {
        open("long.txt")
        expectThat(browser.lines.last()).isEqualTo("    b")
    }

    @Test
    fun `long lines wrap onto extra rows`() {
        open("long.txt")
        expectThat(browser.rows(10, 10))
            .isEqualTo(listOf("a", "x".repeat(10), "x".repeat(10), "x".repeat(5), "    b"))
        expectThat(browser.rows(10, 2)).isEqualTo(listOf("a", "x".repeat(10)))
    }

    @Test
    fun `scrolling stops with the last line on screen when lines wrap`() {
        open("long.txt")
        val visible = browser.linesAtEnd(10, 4)
        expectThat(visible).isEqualTo(2)
        browser.scrollBy(100, visible)
        expectThat(browser.rows(10, 4).last()).isEqualTo("    b")
    }

    @Test
    fun `a line taller than the screen still counts as one visible line`() {
        open("long.txt")
        expectThat(browser.linesAtEnd(2, 1)).isEqualTo(1)
        expectThat(browser.rows(2, 1)).isEqualTo(listOf("a"))
    }
}

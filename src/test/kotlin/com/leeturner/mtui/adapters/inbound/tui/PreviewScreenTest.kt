package com.leeturner.mtui.adapters.inbound.tui

import com.leeturner.mtui.domain.core.model.ProjectFile
import com.leeturner.mtui.domain.core.model.ProjectPreview
import dev.tamboui.tui.event.KeyCode
import dev.tamboui.tui.event.KeyEvent
import org.junit.jupiter.api.Test
import strikt.api.expectThat
import strikt.assertions.isEqualTo
import strikt.assertions.isTrue

class PreviewScreenTest {
    @Test
    fun `a long path loses its leading directories, never its file name`() {
        val path = "src/test/kotlin/com/acme/inventory/service/InventoryServiceTest.kt"
        expectThat(shortenPath(path, 80)).isEqualTo(path)
        expectThat(shortenPath(path, 20)).isEqualTo("…ntoryServiceTest.kt")
        expectThat(shortenPath(path, 10)).isEqualTo("…ceTest.kt")
    }

    @Test
    fun `the file list has its own help and keeps its filter when help closes`() {
        val files = listOf(ProjectFile("README.md", "# App\n"), ProjectFile("build.gradle.kts", ""))
        val browser = FileBrowser(ProjectPreview(files))
        val screen = PreviewScreen(browser)
        expectThat(screen.help.title).isEqualTo("Preview keys")

        screen.handleKey(KeyEvent.ofChar('f'))
        screen.handleKey(KeyEvent.ofChar('R'))
        expectThat(screen.help.title).isEqualTo("File list keys")

        val overlay = HelpOverlay()
        overlay.handleKey(KeyEvent.ofChar('?'), screen.help)
        overlay.handleKey(KeyEvent.ofKey(KeyCode.ESCAPE), screen.help)
        expectThat(browser.listOpen).isTrue()
        expectThat(browser.filter).isEqualTo("R")
        expectThat(screen.help.title).isEqualTo("File list keys")
    }
}

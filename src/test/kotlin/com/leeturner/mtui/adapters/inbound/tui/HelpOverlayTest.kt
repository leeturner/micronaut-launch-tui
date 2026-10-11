package com.leeturner.mtui.adapters.inbound.tui

import dev.tamboui.tui.event.KeyCode
import dev.tamboui.tui.event.KeyEvent
import dev.tamboui.tui.event.KeyModifiers
import org.junit.jupiter.api.Test
import strikt.api.expectThat
import strikt.assertions.isEqualTo
import strikt.assertions.isNull

class HelpOverlayTest {
    private val pickerHelp = Help("Feature picker keys", listOf(KeyHelp("x", "unselect every feature")))
    private val overlay = HelpOverlay()

    @Test
    fun `question mark opens the help the screen offers`() {
        expectThat(overlay.handleKey(KeyEvent.ofChar('?'), pickerHelp)).isEqualTo(HelpResult.HANDLED)
        expectThat(overlay.showing).isEqualTo(pickerHelp)
    }

    @Test
    fun `question mark passes through where the screen offers no help`() {
        expectThat(overlay.handleKey(KeyEvent.ofChar('?'), null)).isEqualTo(HelpResult.PASS)
        expectThat(overlay.showing).isNull()
    }

    @Test
    fun `other keys pass through while help is closed`() {
        expectThat(overlay.handleKey(KeyEvent.ofChar('x'), pickerHelp)).isEqualTo(HelpResult.PASS)
    }

    @Test
    fun `question mark or Esc closes it`() {
        overlay.open(pickerHelp)
        expectThat(overlay.handleKey(KeyEvent.ofChar('?'), pickerHelp)).isEqualTo(HelpResult.HANDLED)
        expectThat(overlay.showing).isNull()

        overlay.open(pickerHelp)
        expectThat(overlay.handleKey(KeyEvent.ofKey(KeyCode.ESCAPE), pickerHelp)).isEqualTo(HelpResult.HANDLED)
        expectThat(overlay.showing).isNull()
    }

    @Test
    fun `other keys are swallowed while it is open`() {
        overlay.open(pickerHelp)
        listOf(KeyEvent.ofChar('x'), KeyEvent.ofKey(KeyCode.ENTER), KeyEvent.ofChar('j')).forEach {
            expectThat(overlay.handleKey(it, pickerHelp)).isEqualTo(HelpResult.HANDLED)
        }
        expectThat(overlay.showing).isEqualTo(pickerHelp)
    }

    @Test
    fun `Ctrl+C quits while it is open`() {
        overlay.open(pickerHelp)
        expectThat(overlay.handleKey(KeyEvent.ofChar('c', KeyModifiers.CTRL), pickerHelp)).isEqualTo(HelpResult.QUIT)
    }
}

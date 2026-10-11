package com.leeturner.mtui.adapters.inbound.tui

import dev.tamboui.widgets.input.TextInputState
import org.junit.jupiter.api.Test
import strikt.api.expectThat
import strikt.assertions.isEqualTo
import strikt.assertions.isFalse
import strikt.assertions.isTrue

class OptionsFormScreenTest {
    @Test
    fun `a question mark just typed in the name is taken out`() {
        val name = TextInputState("demo")
        name.moveCursorToEnd()
        name.insert('?')
        expectThat(name.takeTypedQuestionMark()).isTrue()
        expectThat(name.text()).isEqualTo("demo")
    }

    @Test
    fun `a question mark typed mid-name is taken out where it was typed`() {
        val name = TextInputState("demoapp")
        name.moveCursorToEnd()
        repeat(3) { name.moveCursorLeft() }
        name.insert('?')
        expectThat(name.takeTypedQuestionMark()).isTrue()
        expectThat(name.text()).isEqualTo("demoapp")
        expectThat(name.cursorPosition()).isEqualTo(4)
    }

    @Test
    fun `a name without a question mark before the cursor is left alone`() {
        val name = TextInputState("demo")
        name.moveCursorToEnd()
        expectThat(name.takeTypedQuestionMark()).isFalse()

        val pasted = TextInputState()
        pasted.insert("demo?x")
        expectThat(pasted.takeTypedQuestionMark()).isFalse()
        expectThat(pasted.text()).isEqualTo("demo?x")
    }
}

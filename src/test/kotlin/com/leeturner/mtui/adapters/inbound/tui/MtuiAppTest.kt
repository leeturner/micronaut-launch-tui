package com.leeturner.mtui.adapters.inbound.tui

import org.junit.jupiter.api.Test
import strikt.api.expectThat
import strikt.assertions.all
import strikt.assertions.endsWith
import strikt.assertions.isLessThanOrEqualTo
import strikt.assertions.isTrue

class MtuiAppTest {
    // Otherwise a pasted name arrives key by key, and a ? in it opens help and drops the rest of the paste
    @Test
    fun `a paste arrives whole`() {
        expectThat(MTUI_CONFIG.bracketedPaste()).isTrue()
    }

    @Test
    fun `every hint line ends with the help key and fits an 80 column terminal`() {
        val hints =
            listOf(
                OptionsFormScreen.HINTS,
                FeaturePickerScreen.HINTS,
                PreviewScreen.PREVIEW_HINTS,
                PreviewScreen.FILE_LIST_HINTS,
            )
        expectThat(hints).all { endsWith("· ? help") }
        expectThat(hints.map { it.length }).all { isLessThanOrEqualTo(80) }
    }
}

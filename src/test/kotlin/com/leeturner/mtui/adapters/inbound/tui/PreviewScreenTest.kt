package com.leeturner.mtui.adapters.inbound.tui

import org.junit.jupiter.api.Test
import strikt.api.expectThat
import strikt.assertions.isEqualTo

class PreviewScreenTest {
    @Test
    fun `a long path loses its leading directories, never its file name`() {
        val path = "src/test/kotlin/com/acme/inventory/service/InventoryServiceTest.kt"
        expectThat(shortenPath(path, 80)).isEqualTo(path)
        expectThat(shortenPath(path, 20)).isEqualTo("…ntoryServiceTest.kt")
        expectThat(shortenPath(path, 10)).isEqualTo("…ceTest.kt")
    }
}

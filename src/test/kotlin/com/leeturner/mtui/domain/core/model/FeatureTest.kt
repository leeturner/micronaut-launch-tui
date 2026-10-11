package com.leeturner.mtui.domain.core.model

import org.junit.jupiter.api.Test
import strikt.api.expectThat
import strikt.assertions.isFalse
import strikt.assertions.isTrue

class FeatureTest {
    private val dataJdbc =
        Feature("data-jdbc", "Micronaut Data JDBC", "Data repositories over JDBC", "Database", false, false)

    @Test
    fun `matches name, title or description, ignoring case`() {
        expectThat(dataJdbc.matches("DATA-JDBC")).isTrue()
        expectThat(dataJdbc.matches("micronaut data")).isTrue()
        expectThat(dataJdbc.matches("repositories")).isTrue()
    }

    @Test
    fun `the term is trimmed`() {
        expectThat(dataJdbc.matches("  jdbc ")).isTrue()
    }

    @Test
    fun `a blank term matches`() {
        expectThat(dataJdbc.matches("")).isTrue()
        expectThat(dataJdbc.matches("   ")).isTrue()
    }

    @Test
    fun `an unrelated term doesn't match`() {
        expectThat(dataJdbc.matches("kafka")).isFalse()
    }
}

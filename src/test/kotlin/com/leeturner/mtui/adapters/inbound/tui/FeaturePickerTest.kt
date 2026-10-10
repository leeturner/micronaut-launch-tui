package com.leeturner.mtui.adapters.inbound.tui

import com.leeturner.mtui.domain.core.model.Feature
import org.junit.jupiter.api.Test
import strikt.api.expectThat
import strikt.assertions.isEmpty
import strikt.assertions.isEqualTo
import strikt.assertions.isNull

class FeaturePickerTest {
    private val kapt = feature("kapt", "Development Tools", title = "Kotlin annotation processing")
    private val ksp = feature("ksp", "Development Tools", title = "Kotlin Symbol Processing")
    private val jdbc = feature("data-jdbc", "Database", description = "Adds support for Micronaut Data JDBC")
    private val postgres = feature("postgres", "Database", title = "PostgreSQL")
    private val picker = FeaturePicker(listOf(postgres, ksp, jdbc, kapt))

    @Test
    fun `groups are sorted by category then name`() {
        expectThat(picker.groups).isEqualTo(
            listOf(
                FeatureGroup("Database", listOf(jdbc, postgres)),
                FeatureGroup("Development Tools", listOf(kapt, ksp)),
            ),
        )
    }

    @Test
    fun `search matches name, title and description, ignoring case`() {
        picker.query = "KOTLIN"
        expectThat(visible()).isEqualTo(listOf(kapt, ksp))

        picker.query = "postgres"
        expectThat(visible()).isEqualTo(listOf(postgres))

        picker.query = "micronaut data"
        expectThat(visible()).isEqualTo(listOf(jdbc))
    }

    @Test
    fun `categories with no matches are hidden`() {
        picker.query = "kotlin"

        expectThat(picker.groups.map { it.category }).isEqualTo(listOf("Development Tools"))
    }

    @Test
    fun `search is trimmed and literal`() {
        picker.query = "  ksp "
        expectThat(visible()).isEqualTo(listOf(ksp))

        listOf(".", "c++", "[").forEach {
            picker.query = it
            expectThat(picker.groups).isEmpty()
        }
    }

    @Test
    fun `empty search shows everything`() {
        picker.query = "ksp"
        picker.query = ""

        expectThat(visible()).isEqualTo(listOf(jdbc, postgres, kapt, ksp))
    }

    @Test
    fun `highlight starts on the first visible feature`() {
        expectThat(picker.highlighted).isEqualTo(jdbc)
    }

    @Test
    fun `moving crosses groups and stops at the ends`() {
        picker.moveUp()
        expectThat(picker.highlighted).isEqualTo(jdbc)

        repeat(3) { picker.moveDown() }
        expectThat(picker.highlighted).isEqualTo(ksp)

        picker.moveDown()
        expectThat(picker.highlighted).isEqualTo(ksp)
    }

    @Test
    fun `filtering out the highlight moves it to the first visible feature`() {
        picker.moveDown()
        picker.query = "kotlin"

        expectThat(picker.highlighted).isEqualTo(kapt)
    }

    @Test
    fun `highlight survives a filter that keeps it`() {
        repeat(3) { picker.moveDown() }
        picker.query = "k"

        expectThat(picker.highlighted).isEqualTo(ksp)
    }

    @Test
    fun `nothing matching leaves nothing highlighted`() {
        picker.query = "zzz"
        picker.moveDown()
        picker.moveUp()
        picker.toggle()

        expectThat(picker.highlighted).isNull()
        expectThat(picker.selected).isEmpty()
    }

    @Test
    fun `toggle selects and deselects in selection order`() {
        picker.moveDown()
        picker.toggle()
        picker.moveUp()
        picker.toggle()
        expectThat(picker.selected).isEqualTo(listOf("postgres", "data-jdbc"))

        picker.moveDown()
        picker.toggle()
        expectThat(picker.selected).isEqualTo(listOf("data-jdbc"))
    }

    @Test
    fun `hidden selections stay selected and are cleared by clearSelection`() {
        picker.toggle()
        picker.query = "kotlin"
        expectThat(picker.selected).isEqualTo(listOf("data-jdbc"))

        picker.clearSelection()
        expectThat(picker.selected).isEmpty()
    }

    @Test
    fun `starting selection keeps features that exist, in order`() {
        val restarted = FeaturePicker(listOf(postgres, ksp, jdbc, kapt), selected = listOf("ksp", "gone", "data-jdbc"))

        expectThat(restarted.selected).isEqualTo(listOf("ksp", "data-jdbc"))
    }

    @Test
    fun `a name passed twice is selected once`() {
        val restarted = FeaturePicker(listOf(postgres, ksp, jdbc, kapt), selected = listOf("ksp", "ksp"))

        expectThat(restarted.selected).isEqualTo(listOf("ksp"))
    }

    private fun visible() = picker.groups.flatMap { it.features }

    private fun feature(
        name: String,
        category: String,
        title: String = name,
        description: String = "",
    ) = Feature(name, title, description, category, preview = false, community = false)
}

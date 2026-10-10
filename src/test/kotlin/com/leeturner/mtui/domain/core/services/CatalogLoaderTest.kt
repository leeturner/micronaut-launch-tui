package com.leeturner.mtui.domain.core.services

import arrow.core.Either
import arrow.core.left
import arrow.core.nonEmptyListOf
import arrow.core.right
import com.leeturner.mtui.domain.core.model.ApplicationType
import com.leeturner.mtui.domain.core.model.BuildType
import com.leeturner.mtui.domain.core.model.CatalogError
import com.leeturner.mtui.domain.core.model.EmptySelectOptionsError
import com.leeturner.mtui.domain.core.model.Feature
import com.leeturner.mtui.domain.core.model.FeaturesError
import com.leeturner.mtui.domain.core.model.JdkVersion
import com.leeturner.mtui.domain.core.model.Language
import com.leeturner.mtui.domain.core.model.SelectOptions
import com.leeturner.mtui.domain.core.model.SelectOptionsError
import com.leeturner.mtui.domain.core.model.TestFramework
import com.leeturner.mtui.domain.core.ports.FeatureRetriever
import com.leeturner.mtui.domain.core.ports.SelectOptionRetriever
import org.junit.jupiter.api.Test
import strikt.api.expectThat
import strikt.arrow.isLeft
import strikt.arrow.isRight
import strikt.assertions.isEqualTo
import strikt.assertions.isNull

class CatalogLoaderTest {
    private val cli = ApplicationType(title = "", name = "", description = "", value = "CLI", label = "")
    private val kotlin = Language(extension = "", description = "", name = "", value = "KOTLIN", label = "")
    private val options =
        SelectOptions(
            types = nonEmptyListOf(cli),
            defaultType = cli,
            jdkVersions = nonEmptyListOf(JdkVersion("", "", "JDK_25", "")),
            defaultJdkVersion = JdkVersion("", "", "JDK_25", ""),
            languages = nonEmptyListOf(kotlin),
            defaultLanguage = kotlin,
            testFrameworks = nonEmptyListOf(TestFramework("", "", "JUNIT", "")),
            defaultTestFramework = TestFramework("", "", "JUNIT", ""),
            buildTypes = nonEmptyListOf(BuildType("", "GRADLE", "")),
            defaultBuildType = BuildType("", "GRADLE", ""),
        )
    private val ksp = Feature("ksp", "KSP", "", "Development Tools", preview = false, community = false)
    private val selectOptionRetriever = FakeSelectOptionRetriever()
    private val featureRetriever = FakeFeatureRetriever()
    private val loader = CatalogLoader(selectOptionRetriever, featureRetriever)

    @Test
    fun `features are loaded for the default type and language`() {
        expectThat(loader.load()).isRight().and {
            get { value.options }.isEqualTo(options)
            get { value.features }.isEqualTo(listOf(ksp))
        }
        expectThat(featureRetriever.requested).isEqualTo(cli to kotlin)
    }

    @Test
    fun `select options failure is reported and features are not fetched`() {
        selectOptionRetriever.result = EmptySelectOptionsError("No application types").left()

        expectThat(loader.load()).isLeft().get { value }.isEqualTo(CatalogError("No application types"))
        expectThat(featureRetriever.requested).isNull()
    }

    @Test
    fun `features failure is reported`() {
        featureRetriever.result = FeaturesError(500, "Server Error").left()

        expectThat(loader.load()).isLeft().get { value }.isEqualTo(CatalogError("Server Error"))
    }

    private inner class FakeSelectOptionRetriever : SelectOptionRetriever {
        var result: Either<SelectOptionsError, SelectOptions> = options.right()

        override fun getSelectOptions() = result
    }

    private inner class FakeFeatureRetriever : FeatureRetriever {
        var result: Either<FeaturesError, List<Feature>> = listOf(ksp).right()
        var requested: Pair<ApplicationType, Language>? = null

        override fun getFeatures(
            type: ApplicationType,
            language: Language,
        ): Either<FeaturesError, List<Feature>> {
            requested = type to language
            return result
        }
    }
}

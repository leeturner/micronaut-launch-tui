package com.leeturner.mtui

import com.tngtech.archunit.base.DescribedPredicate.alwaysTrue
import com.tngtech.archunit.core.domain.JavaClass.Predicates.resideInAPackage
import com.tngtech.archunit.core.importer.ClassFileImporter
import com.tngtech.archunit.core.importer.ImportOption
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes
import com.tngtech.archunit.library.Architectures.onionArchitecture
import org.junit.jupiter.api.Test

class ArchitectureTest {
    private val classes =
        ClassFileImporter()
            .withImportOption(ImportOption.DoNotIncludeTests())
            .importPackages("com.leeturner.mtui")

    @Test
    fun `layers follow ports and adapters`() {
        onionArchitecture()
            .domainModels("com.leeturner.mtui.domain.core.model..")
            .domainServices("com.leeturner.mtui.domain.core.services..", "com.leeturner.mtui.domain.core.ports..")
            .adapter("tui", "com.leeturner.mtui.adapters.inbound.tui..")
            .adapter("http", "com.leeturner.mtui.adapters.outbound.http..")
            .adapter("filesystem", "com.leeturner.mtui.adapters.outbound.filesystem..")
            // There is no application-services layer
            .withOptionalLayers(true)
            // The root package is the composition root: it wires the layers together, so it may depend on any of them
            .ignoreDependency(resideInAPackage("com.leeturner.mtui"), alwaysTrue())
            .check(classes)
    }

    @Test
    fun `the domain is free of frameworks`() {
        classes()
            .that()
            .resideInAPackage("com.leeturner.mtui.domain..")
            .should()
            .onlyDependOnClassesThat()
            .resideInAnyPackage(
                "com.leeturner.mtui.domain..",
                "kotlin..",
                "java..",
                "arrow..",
                "org.jetbrains.annotations..",
            ).check(classes)
    }
}

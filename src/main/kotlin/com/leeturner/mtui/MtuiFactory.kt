package com.leeturner.mtui

import com.leeturner.mtui.domain.core.ports.LaunchCatalog
import com.leeturner.mtui.domain.core.ports.ProjectCreator
import com.leeturner.mtui.domain.core.ports.ProjectWriter
import com.leeturner.mtui.domain.core.services.CatalogLoader
import com.leeturner.mtui.domain.core.services.ProjectGenerator
import io.micronaut.context.annotation.Factory
import jakarta.inject.Singleton

// The domain services know nothing of Micronaut, so they are wired here, in the composition root
@Factory
class MtuiFactory {
    @Singleton
    fun catalogLoader(catalog: LaunchCatalog): CatalogLoader = CatalogLoader(catalog)

    @Singleton
    fun projectGenerator(
        creator: ProjectCreator,
        writer: ProjectWriter,
    ): ProjectGenerator = ProjectGenerator(creator, writer)
}

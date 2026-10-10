package com.leeturner.mtui

import com.leeturner.mtui.adapters.inbound.tui.MtuiApp
import com.leeturner.mtui.adapters.inbound.tui.MtuiOutcome
import com.leeturner.mtui.domain.core.services.CatalogLoader
import com.leeturner.mtui.domain.core.services.ProjectGenerator
import io.micronaut.configuration.picocli.PicocliRunner
import jakarta.inject.Inject
import picocli.CommandLine.Command
import java.nio.file.Path
import java.util.concurrent.Callable
import kotlin.system.exitProcess

@Command(
    name = "mtui",
    description = ["Create Micronaut projects from the terminal"],
    mixinStandardHelpOptions = true,
)
class MtuiCommand
    @Inject
    constructor(
        private val catalogLoader: CatalogLoader,
        private val projectGenerator: ProjectGenerator,
    ) : Callable<Int> {
        override fun call(): Int {
            val app = MtuiApp(catalogLoader, projectGenerator, Path.of("").toAbsolutePath())
            app.run()
            return when (val outcome = app.outcome) {
                is MtuiOutcome.Created -> {
                    println(outcome.path)
                    println("cd ${outcome.path.fileName}")
                    0
                }

                is MtuiOutcome.Failed -> {
                    System.err.println(outcome.message)
                    1
                }

                MtuiOutcome.Cancelled -> {
                    1
                }
            }
        }

        companion object {
            @JvmStatic
            fun main(args: Array<String>) {
                exitProcess(PicocliRunner.execute(MtuiCommand::class.java, *args))
            }
        }
    }

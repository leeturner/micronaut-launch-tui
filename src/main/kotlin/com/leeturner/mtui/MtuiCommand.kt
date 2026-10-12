package com.leeturner.mtui

import com.leeturner.mtui.adapters.inbound.tui.MtuiApp
import com.leeturner.mtui.adapters.inbound.tui.MtuiOutcome
import com.leeturner.mtui.domain.core.services.CatalogLoader
import com.leeturner.mtui.domain.core.services.ProjectGenerator
import io.micronaut.configuration.picocli.PicocliRunner
import jakarta.inject.Inject
import picocli.CommandLine.Command
import picocli.CommandLine.Option
import java.nio.file.Path
import java.util.concurrent.Callable
import kotlin.system.exitProcess

@Command(
    name = "mtui",
    description = ["Create Micronaut projects from the terminal"],
    mixinStandardHelpOptions = true,
    versionProvider = MtuiVersion::class,
)
class MtuiCommand
    @Inject
    constructor(
        private val catalogLoader: CatalogLoader,
        private val projectGenerator: ProjectGenerator,
    ) : Callable<Int> {
        @Option(
            names = ["--open"],
            paramLabel = "<command>",
            description = [
                "Command to open the project with once it is created, e.g. idea. " +
                    "Defaults to the MTUI_OPEN environment variable.",
            ],
        )
        var open: String? = null

        override fun call(): Int {
            val app = MtuiApp(catalogLoader, projectGenerator, Path.of("").toAbsolutePath())
            app.run()
            return when (val outcome = app.outcome) {
                is MtuiOutcome.Created -> {
                    println(outcome.path)
                    println("cd ${outcome.path.fileName}")
                    openCreated(outcome.path)
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

        private fun openCreated(path: Path): Int {
            val command = openCommand(open, System.getenv("MTUI_OPEN")) ?: return 0
            return openProject(command, path).fold(
                { reason ->
                    System.err.println("Could not open the project with \"${command.joinToString(" ")}\": $reason")
                    1
                },
                { 0 },
            )
        }

        companion object {
            @JvmStatic
            fun main(args: Array<String>) {
                exitProcess(PicocliRunner.execute(MtuiCommand::class.java, *args))
            }
        }
    }

package com.leeturner.mtui

import io.micronaut.configuration.picocli.PicocliRunner
import picocli.CommandLine.Command
import picocli.CommandLine.Option
import java.util.concurrent.Callable
import kotlin.system.exitProcess

@Command(
    name = "mtui",
    description = ["..."],
    mixinStandardHelpOptions = true,
)
class MtuiCommand : Callable<Int> {
    @Option(names = ["-v", "--verbose"], description = ["..."])
    private var verbose: Boolean = false

    override fun call(): Int {
        // business logic here
        if (verbose) {
            println("Hi!")
        }
        return 0 // success
    }

    companion object {
        @JvmStatic
        fun main(args: Array<String>) {
            val exitCode = PicocliRunner.call(MtuiCommand::class.java, *args)
            exitProcess(exitCode ?: 0)
        }
    }
}

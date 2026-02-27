package com.example

import io.micronaut.configuration.picocli.PicocliRunner
import picocli.CommandLine.Command
import picocli.CommandLine.Option
import java.util.concurrent.Callable
import kotlin.system.exitProcess

@Command(
    name = "demo",
    description = ["..."],
    mixinStandardHelpOptions = true,
)
class DemoCommand : Callable<Int> {
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
        @JvmStatic fun main(args: Array<String>) {
            val exitCode = PicocliRunner.call(DemoCommand::class.java, *args)
            exitProcess(exitCode ?: 0)
        }
    }
}

package com.leeturner.mtui

import io.micronaut.configuration.picocli.PicocliRunner
import io.micronaut.context.ApplicationContext
import io.micronaut.context.env.Environment.CLI
import io.micronaut.context.env.Environment.TEST
import org.junit.jupiter.api.Test
import strikt.api.expectThat
import strikt.assertions.contains
import strikt.assertions.isEqualTo
import java.io.ByteArrayOutputStream
import java.io.PrintStream

class MtuiCommandTest {
    @Test
    fun testWithCommandLineOption() {
        ApplicationContext.run(CLI, TEST).use { ctx ->
            ByteArrayOutputStream().use { baos ->
                System.setOut(PrintStream(baos))

                val args = arrayOf("-v")
                val exitCode = PicocliRunner.execute(MtuiCommand::class.java, ctx, *args)

                expectThat(exitCode).isEqualTo(0)
                expectThat(baos.toString()).contains("Hi!")
            }
        }
    }

    @Test
    fun `unknown option returns a non-zero exit code`() {
        ApplicationContext.run(CLI, TEST).use { ctx ->
            val exitCode = PicocliRunner.execute(MtuiCommand::class.java, ctx, "--bogus")

            expectThat(exitCode).isEqualTo(2)
        }
    }
}

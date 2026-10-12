package com.leeturner.mtui

import io.micronaut.context.annotation.Value
import jakarta.inject.Singleton
import picocli.CommandLine

// A bean, so Micronaut's picocli factory creates it without reflection in the native image
@Singleton
class MtuiVersion(
    @Value("\${mtui.version}") private val version: String,
) : CommandLine.IVersionProvider {
    override fun getVersion(): Array<String> = arrayOf(version)
}

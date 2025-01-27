/*
 * Copyright (c) 2025 Oleg Yukhnevich. Use of this source code is governed by the Apache 2.0 license.
 */

package dev.whyoleg.sweetspi.compiler

import org.jetbrains.kotlin.compiler.plugin.*
import org.jetbrains.kotlin.config.*
import kotlin.io.path.*

@OptIn(ExperimentalCompilerApi::class)
class SweetCommandLineProcessor : CommandLineProcessor {
    override val pluginId: String = "dev.whyoleg.sweetspi"
    override val pluginOptions: Collection<CliOption> = listOf(
        RESOURCES_PATH
    )

    override fun processOption(
        option: AbstractCliOption,
        value: String,
        configuration: CompilerConfiguration,
    ): Unit = when (option.optionName) {
        RESOURCES_PATH.optionName -> configuration.put(SweetConfigurationKeys.RESOURCES_PATH, Path(value))
        else                      -> error("Unknown plugin option: ${option.optionName}")
    }

    companion object {
        private val RESOURCES_PATH = CliOption(
            optionName = "resourcesPath",
            valueDescription = "<path>",
            description = SweetConfigurationKeys.RESOURCES_PATH.toString(),
            required = true,
            allowMultipleOccurrences = false,
        )
    }
}

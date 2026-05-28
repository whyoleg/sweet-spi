/*
 * Copyright (c) 2025 Oleg Yukhnevich. Use of this source code is governed by the Apache 2.0 license.
 */

package dev.whyoleg.sweetspi.compiler

import org.jetbrains.kotlin.backend.common.extensions.*
import org.jetbrains.kotlin.compiler.plugin.*
import org.jetbrains.kotlin.config.*
import org.jetbrains.kotlin.fir.extensions.*
import java.nio.file.*
import kotlin.io.path.*

private object SweetConfigurationKeys {
    val RESOURCES_PATH = CompilerConfigurationKey<Path>("Path to resources dir")
    val MODULE_INFO_PROPERTIES_PATH = CompilerConfigurationKey<Path>("Path to module-info.properties file")
}

@OptIn(ExperimentalCompilerApi::class)
class SweetCommandLineProcessor : CommandLineProcessor {
    override val pluginId: String = "dev.whyoleg.sweetspi"
    override val pluginOptions: Collection<CliOption> = listOf(
        RESOURCES_PATH,
        MODULE_INFO_PROPERTIES_PATH
    )

    override fun processOption(
        option: AbstractCliOption,
        value: String,
        configuration: CompilerConfiguration,
    ): Unit = when (option.optionName) {
        RESOURCES_PATH.optionName              -> configuration.put(SweetConfigurationKeys.RESOURCES_PATH, Path(value))
        MODULE_INFO_PROPERTIES_PATH.optionName -> configuration.put(SweetConfigurationKeys.MODULE_INFO_PROPERTIES_PATH, Path(value))
        else                                   -> error("Unknown plugin option: ${option.optionName}")
    }

    companion object {
        private val RESOURCES_PATH = CliOption(
            optionName = "resourcesPath",
            valueDescription = "<path>",
            description = SweetConfigurationKeys.RESOURCES_PATH.toString(),
            required = false, // only for JVM
            allowMultipleOccurrences = false,
        )
        private val MODULE_INFO_PROPERTIES_PATH = CliOption(
            optionName = "moduleInfoPropertiesPath",
            valueDescription = "<path>",
            description = SweetConfigurationKeys.MODULE_INFO_PROPERTIES_PATH.toString(),
            required = false, // only for JVM
            allowMultipleOccurrences = false,
        )
    }
}

@OptIn(ExperimentalCompilerApi::class)
class SweetCompilerPluginRegistrar : CompilerPluginRegistrar() {
    override val pluginId: String get() = "dev.whyoleg.sweetspi"
    override val supportsK2: Boolean get() = true

    override fun ExtensionStorage.registerExtensions(configuration: CompilerConfiguration) {
        val resourcesPath = configuration.get(SweetConfigurationKeys.RESOURCES_PATH)
        val moduleInfoPropertiesPath = configuration.get(SweetConfigurationKeys.MODULE_INFO_PROPERTIES_PATH)

        FirExtensionRegistrarAdapter.registerExtension(SweetFirExtensionRegistrar())
        IrGenerationExtension.registerExtension(SweetIrGenerationExtension(resourcesPath, moduleInfoPropertiesPath))
    }
}

class SweetFirExtensionRegistrar : FirExtensionRegistrar() {
    override fun ExtensionRegistrarContext.configurePlugin() {
        +::SweetFirCheckers
    }
}

/*
 * Copyright (c) 2025 Oleg Yukhnevich. Use of this source code is governed by the Apache 2.0 license.
 */

package dev.whyoleg.sweetspi.compiler

import dev.whyoleg.sweetspi.compiler.ir.*
import org.jetbrains.kotlin.backend.common.extensions.*
import org.jetbrains.kotlin.compiler.plugin.*
import org.jetbrains.kotlin.config.*
import org.jetbrains.kotlin.ir.util.*

@OptIn(ExperimentalCompilerApi::class)
class SweetCompilerPluginRegistrar : CompilerPluginRegistrar() {
    override val supportsK2: Boolean get() = true

    override fun ExtensionStorage.registerExtensions(configuration: CompilerConfiguration) {
        //FirExtensionRegistrarAdapter.registerExtension(SweetFirExtensionRegistrar())
        IrGenerationExtension.registerExtension(
            SweetIrGenerationExtension(
                logger = configuration.irMessageLogger,
                resourcesPath = configuration.getNotNull(SweetConfigurationKeys.RESOURCES_PATH)
            )
        )
        IrGenerationExtension.registerExtension(
            JvmSweetIrGenerationExtension(
                logger = configuration.irMessageLogger,
                resourcesPath = configuration.getNotNull(SweetConfigurationKeys.RESOURCES_PATH)
            )
        )
        IrGenerationExtension.registerExtension(
            KlibSweetIrGenerationExtension(
                logger = configuration.irMessageLogger
            )
        )
    }
}

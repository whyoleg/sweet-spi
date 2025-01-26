/*
 * Copyright (c) 2025 Oleg Yukhnevich. Use of this source code is governed by the Apache 2.0 license.
 */

@file:OptIn(ExperimentalCompilerApi::class)

package dev.whyoleg.sweetspi.compiler

import org.jetbrains.kotlin.backend.common.extensions.*
import org.jetbrains.kotlin.compiler.plugin.*
import org.jetbrains.kotlin.config.*
import org.jetbrains.kotlin.fir.*
import org.jetbrains.kotlin.fir.analysis.extensions.*
import org.jetbrains.kotlin.fir.extensions.*
import org.jetbrains.kotlin.ir.declarations.*

// TODO: we need FIR only for checkers!! - everything else should be on IR level
// we should lower IrFile, find all TOP_LEVEL services and generate initializers for them

// here should be:
// - find all @Service/@ServiceProvider/@JvmService/@JvmServiceProvider on class-likes
// - for JVM:
//    - (step1) for `@Service` - generate an additional interface (@PublishedApi internal) (FIR + IR)
//    - (step1) for `@ServiceProvider` - generate an additional interface impl and meta-inf (FIR + IR + RESOURCES)
//    - (step2) for `ServiceLoader.load` - intrinsic for R8 optimizable (IR)
//    - (step3) for `@JvmService` - do nothing
//    - (step3) for `@JvmServiceProvider` - generate meta-inf (RESOURCES)
// -    (step1) for klib - generate init with `@EagerInitializer` (FIR + IR)

// klib:
// - if annotated -> generate call
// jvm:
// - if annotated -> generate a lot of different things :)


class SweetCommandLineProcessor : CommandLineProcessor {
    override val pluginId: String = "dev.whyoleg.sweetspi"
    override val pluginOptions: Collection<CliOption> = emptyList()
}

class SweetCompilerPluginRegistrar : CompilerPluginRegistrar() {
    override val supportsK2: Boolean get() = true

    override fun ExtensionStorage.registerExtensions(configuration: CompilerConfiguration) {
        FirExtensionRegistrarAdapter.registerExtension(SweetFirExtensionRegistrar())
        IrGenerationExtension.registerExtension(SweetIrGenerationExtension(configuration))
    }
}

private class SweetFirExtensionRegistrar : FirExtensionRegistrar() {
    override fun ExtensionRegistrarContext.configurePlugin() {
        +::FirCheckers
    }
}

private class SweetIrGenerationExtension(private val configuration: CompilerConfiguration) : IrGenerationExtension {
    override fun generate(moduleFragment: IrModuleFragment, pluginContext: IrPluginContext) {
        // generate based on service/serviceProvider based on platform
    }
}

private class FirCheckers(session: FirSession) : FirAdditionalCheckersExtension(session) {
    // checkers - annotation targets clarification?
}

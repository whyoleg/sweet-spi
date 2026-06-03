/*
 * Copyright (c) 2026 Oleg Yukhnevich. Use of this source code is governed by the Apache 2.0 license.
 */

package dev.whyoleg.sweetspi.compiler.ir

import dev.whyoleg.sweetspi.compiler.*
import org.jetbrains.kotlin.backend.common.extensions.*
import org.jetbrains.kotlin.ir.declarations.*
import org.jetbrains.kotlin.platform.jvm.*
import java.nio.file.*

/**
 * Orchestrates IR generation. The ABI-visible nested declarations are produced in **FIR**
 * ([dev.whyoleg.sweetspi.compiler.fir.SweetFirDeclarationGenerator]): `X$ServiceProvider` (common) and,
 * on klib, `X$ServiceProviderRegistry`. IR only fills bodies and emits non-ABI artifacts:
 *
 * - `@Provides` → provider-implementation class (+ klib `@EagerInitialization` registration; JVM impl-id
 *   bookkeeping) — [ServiceProviderImplementationGenerator].
 * - `@Service` (klib) → `@ServiceProviderRegistryHolder` associated-object annotation on the service —
 *   [ServiceRegistryHolderGenerator].
 * - JVM → `META-INF/services` + `module-info.properties` from collected packages / usages / providers —
 *   [generateJvmServices] / [generateModuleInfoProperties], [PackagesCollector] / [ServiceLoaderUsagesCollector].
 */
class SweetIrGenerationExtension(
    private val resourcesPath: Path?,
    private val moduleInfoPropertiesPath: Path?,
) : IrGenerationExtension {
    override fun generate(moduleFragment: IrModuleFragment, pluginContext: IrPluginContext): Unit = when {
        pluginContext.platform.isJvm() -> generateJvm(moduleFragment, JvmSweetIrPluginContext(pluginContext))
        else                           -> generateKlib(moduleFragment, KlibSweetIrPluginContext(pluginContext))
    }

    private fun generateKlib(moduleFragment: IrModuleFragment, pluginContext: KlibSweetIrPluginContext) {
        ServiceRegistryHolderGenerator.generate(pluginContext, moduleFragment)
        ServiceProviderImplementationGenerator.generate(pluginContext, moduleFragment)
    }

    private fun generateJvm(moduleFragment: IrModuleFragment, pluginContext: JvmSweetIrPluginContext) {
        requireNotNull(resourcesPath) { "resourcesPath cannot be null" }
        requireNotNull(moduleInfoPropertiesPath) { "moduleInfoPropertiesPath cannot be null" }

        val generatedProviderImplementations = ServiceProviderImplementationGenerator.generate(pluginContext, moduleFragment)
            .map { (serviceId, serviceImplIds) -> serviceId.toJvmString() to serviceImplIds.map { it.toJvmString() } }
            .toMap()

        val collectedPackages = PackagesCollector.collectPackages(moduleFragment)
        val collectedUsages = ServiceLoaderUsagesCollector.collectUsages(moduleFragment).map { it.toJvmString() }

        generateJvmServices(
            resourcesPath = resourcesPath,
            generatedProviderImplementations = generatedProviderImplementations
        )
        generateModuleInfoProperties(
            moduleInfoPropertiesPath = moduleInfoPropertiesPath,
            collectedPackages = collectedPackages,
            collectedServiceLoaderUsages = collectedUsages,
            generatedProviderImplementations = generatedProviderImplementations
        )
    }
}

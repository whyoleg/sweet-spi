/*
 * Copyright (c) 2026 Oleg Yukhnevich. Use of this source code is governed by the Apache 2.0 license.
 */

package dev.whyoleg.sweetspi.compiler.ir

import dev.whyoleg.sweetspi.compiler.*
import org.jetbrains.kotlin.backend.common.*
import org.jetbrains.kotlin.ir.declarations.*
import org.jetbrains.kotlin.ir.expressions.impl.*
import org.jetbrains.kotlin.ir.types.*
import org.jetbrains.kotlin.ir.util.*

// @Service (klib only): the `$ServiceProvider` interface and `$ServiceProviderRegistry` object are
// generated in FIR (SweetFirDeclarationGenerator). Here IR only adds the `@ServiceProviderRegistryHolder`
// associated-object annotation on the service, pointing at the FIR-generated registry object, so the
// runtime can resolve the registry via `findAssociatedObject`. JVM uses META-INF/services instead.
internal class ServiceRegistryHolderGenerator private constructor(
    private val pluginContext: KlibSweetIrPluginContext,
) : ClassLoweringPass {
    override fun lower(irClass: IrClass) {
        if (!irClass.hasAnnotation(SweetNames.Service)) return

        val registryClass = irClass.findDeclaration<IrClass> { it.name == SweetNames.ServiceProviderRegistryName }
            ?: error("FIR-generated ${SweetNames.ServiceProviderRegistryName} not found for service ${irClass.name}")

        irClass.annotateWith(pluginContext.serviceProviderRegistryHolderClass) {
            arguments[0] = IrClassReferenceImpl(
                startOffset = startOffset,
                endOffset = endOffset,
                type = pluginContext.irBuiltIns.kClassClass.defaultType,
                symbol = registryClass.symbol,
                classType = registryClass.defaultType
            )
        }
    }

    companion object {
        fun generate(pluginContext: KlibSweetIrPluginContext, moduleFragment: IrModuleFragment) {
            ServiceRegistryHolderGenerator(pluginContext).lower(moduleFragment)
        }
    }
}

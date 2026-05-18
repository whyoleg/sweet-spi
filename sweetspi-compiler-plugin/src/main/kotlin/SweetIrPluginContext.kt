/*
 * Copyright (c) 2026 Oleg Yukhnevich. Use of this source code is governed by the Apache 2.0 license.
 */

package dev.whyoleg.sweetspi.compiler

import org.jetbrains.kotlin.backend.common.extensions.*
import org.jetbrains.kotlin.builtins.*
import org.jetbrains.kotlin.ir.declarations.*
import org.jetbrains.kotlin.ir.expressions.impl.*
import org.jetbrains.kotlin.ir.symbols.*
import org.jetbrains.kotlin.ir.types.*
import org.jetbrains.kotlin.ir.util.*
import org.jetbrains.kotlin.name.*
import org.jetbrains.kotlin.platform.*
import org.jetbrains.kotlin.platform.konan.*

val SweetOrigin: IrDeclarationOrigin = IrDeclarationOriginImpl("SWEET_SPI", isSynthetic = true)

sealed class SweetIrPluginContext(context: IrPluginContext) : IrPluginContext by context {
    val anyConstructor = irBuiltIns.anyClass.owner.constructors.single()
    val publishedApiClass = finderForBuiltins().findClass(StandardClassIds.Annotations.PublishedApi)!!
    val serviceProviderClass = finderForBuiltins().findClass(SweetNames.ServiceProvider)!!

    // lazy
    val lazyFunctionSymbol = finderForBuiltins().findFunctions(
        CallableId(StandardNames.BUILT_INS_PACKAGE_FQ_NAME, Name.identifier("lazy"))
    ).single {
        it.owner.hasShape(regularParameters = 1)
    }
    val lazyClass = finderForBuiltins().findClass(
        ClassId(StandardNames.BUILT_INS_PACKAGE_FQ_NAME, Name.identifier("Lazy"))
    )!!.owner
    val lazyValueGetter = lazyClass.getPropertyGetter("value")!!
    val lazyValueIsInitialized = lazyClass.getSimpleFunction("isInitialized")!!
}

class JvmSweetIrPluginContext(context: IrPluginContext) : SweetIrPluginContext(context)

class KlibSweetIrPluginContext(context: IrPluginContext) : SweetIrPluginContext(context) {
    val serviceProviderRegistryHolderClass = finderForBuiltins().findClass(SweetNames.ServiceProviderRegistryHolder)!!
    val serviceProviderRegistryClass = finderForBuiltins().findClass(SweetNames.ServiceProviderRegistry)!!
    val serviceProviderRegistryConstructor = finderForBuiltins().findConstructors(SweetNames.ServiceProviderRegistry).single()
    val serviceProviderRegistryRegister = finderForBuiltins().findFunctions(
        CallableId(SweetNames.ServiceProviderRegistry, Name.identifier("register"))
    ).single()

    // eager init
    val eagerInitializationClass = finderForBuiltins().findClass(
        when {
            context.platform.isNative() -> ClassId(
                packageFqName = FqName("kotlin.native"),
                topLevelName = Name.identifier("EagerInitialization")
            )
            context.platform.isJs()     -> ClassId(
                packageFqName = FqName("kotlin.js"),
                topLevelName = Name.identifier("EagerInitialization")
            )
            context.platform.isWasm()   -> ClassId(
                packageFqName = FqName("kotlin"),
                topLevelName = Name.identifier("EagerInitialization")
            )
            else                        -> error("Unsupported platform: ${context.platform}")
        }
    )!!
}

internal fun IrMutableAnnotationContainer.annotateWith(
    annotationSymbol: IrClassSymbol,
    block: IrAnnotationImpl.() -> Unit = {},
) {
    val annotationCtor = annotationSymbol.constructors.single { it.owner.isPrimary }
    val annotationType = annotationSymbol.defaultType

    annotations += IrAnnotationImpl.fromSymbolOwner(startOffset, endOffset, annotationType, annotationCtor).apply(block)
}

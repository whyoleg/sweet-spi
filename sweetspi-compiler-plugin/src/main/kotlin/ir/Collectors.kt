/*
 * Copyright (c) 2026 Oleg Yukhnevich. Use of this source code is governed by the Apache 2.0 license.
 */

package dev.whyoleg.sweetspi.compiler.ir

import dev.whyoleg.sweetspi.compiler.*
import org.jetbrains.kotlin.backend.common.*
import org.jetbrains.kotlin.ir.declarations.*
import org.jetbrains.kotlin.ir.expressions.*
import org.jetbrains.kotlin.ir.util.*
import org.jetbrains.kotlin.ir.visitors.*
import org.jetbrains.kotlin.name.*

// Collects every package in the module, for the JVM `module-info.properties` `# exports` section.
internal class PackagesCollector private constructor(
    private val collectedPackages: MutableSet<String>,
) : FileLoweringPass {
    override fun lower(irFile: IrFile) {
        collectedPackages.add(irFile.packageFqName.asString())
    }

    companion object {
        fun collectPackages(moduleFragment: IrModuleFragment): List<String> {
            val collectedPackages = mutableSetOf<String>()
            PackagesCollector(collectedPackages).lower(moduleFragment)
            return collectedPackages.toList()
        }
    }
}

// Collects ServiceLoader.load(...) usages, for the JVM `module-info.properties` `# uses` section.
// TODO(M2): implement extraction of the service ClassId from the call's type / value argument. The
//  reified `load<T>()` is inline — confirm how the type argument survives to IR (this finding also
//  informs M4). Until then this returns an empty set and `# uses` stays empty.
internal class ServiceLoaderUsagesCollector private constructor(
    private val collectedUsages: MutableSet<ClassId>,
) : BodyLoweringPass, IrVisitorVoid() {

    override fun visitCall(expression: IrCall) {
        if (expression.symbol.owner.callableId in SweetNames.AllServiceLoaderCalls) {
            // TODO(M2): collectedUsages.add(<service ClassId from expression>)
        }
        super.visitCall(expression)
    }

    override fun lower(irBody: IrBody, container: IrDeclaration) {
        irBody.accept(this, null)
    }

    companion object {
        fun collectUsages(moduleFragment: IrModuleFragment): Set<ClassId> {
            val collectedUsages = mutableSetOf<ClassId>()
            // ServiceLoaderUsagesCollector(collectedUsages).lower(moduleFragment) // enabled in M2
            return collectedUsages
        }
    }
}

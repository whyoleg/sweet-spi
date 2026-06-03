/*
 * Copyright (c) 2026 Oleg Yukhnevich. Use of this source code is governed by the Apache 2.0 license.
 */

package dev.whyoleg.sweetspi.compiler.fir

import org.jetbrains.kotlin.fir.*
import org.jetbrains.kotlin.fir.analysis.checkers.declaration.*
import org.jetbrains.kotlin.fir.analysis.extensions.*
import org.jetbrains.kotlin.fir.extensions.*

class SweetFirExtensionRegistrar : FirExtensionRegistrar() {
    override fun ExtensionRegistrarContext.configurePlugin() {
        +::SweetFirCheckers
        +::SweetFirDeclarationGenerator
    }
}

// checkers that annotations are applied to correct functions/properties/classes
private class SweetFirCheckers(session: FirSession) : FirAdditionalCheckersExtension(session) {
    override val declarationCheckers: DeclarationCheckers = object : DeclarationCheckers() {
        override val regularClassCheckers: Set<FirRegularClassChecker> = setOf(ServiceChecker, ProvidesClassChecker)
        override val simpleFunctionCheckers: Set<FirSimpleFunctionChecker> = setOf(ProvidesFunctionChecker)
        override val propertyCheckers: Set<FirPropertyChecker> = setOf(ProvidesPropertyChecker)
    }
}

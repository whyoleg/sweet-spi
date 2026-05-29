/*
 * Copyright (c) 2025 Oleg Yukhnevich. Use of this source code is governed by the Apache 2.0 license.
 */

package dev.whyoleg.sweetspi.compiler

import org.jetbrains.kotlin.fir.*
import org.jetbrains.kotlin.fir.analysis.extensions.*

// checkers that annotations are applied to correct functions/properties/classes
class SweetFirCheckers(session: FirSession) : FirAdditionalCheckersExtension(session) {
    // check that one class is not annotated with both @Provides and @Service
    // check that @Provides argument is annotated with @Service
    // check that @JvmProvides argument is NOT annotated with @Service
    // check that class supser types or function/property return types has X from @Provides(X)
    // check that there is no receiver, no context parameters
    // check that there is constructor/function with no arguments or with all default arguments
    // check that the class is not inner
}

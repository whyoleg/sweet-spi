/*
 * Copyright (c) 2025 Oleg Yukhnevich. Use of this source code is governed by the Apache 2.0 license.
 */

package dev.whyoleg.sweetspi.compiler

import org.jetbrains.kotlin.fir.*
import org.jetbrains.kotlin.fir.analysis.extensions.*

// checkers that annotations are applied to correct functions/properties/classes
class SweetFirCheckers(session: FirSession) : FirAdditionalCheckersExtension(session) {

}

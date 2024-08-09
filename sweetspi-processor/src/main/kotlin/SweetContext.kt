/*
 * Copyright (c) 2024 Oleg Yukhnevich. Use of this source code is governed by the Apache 2.0 license.
 */

package dev.whyoleg.sweetspi.processor

import com.google.devtools.ksp.processing.*
import com.google.devtools.ksp.symbol.*

class SweetContext(
    val packageName: String,
    val dependencies: Dependencies,
    val services: List<KSClassDeclaration>,
    val serviceProviders: Map<KSType, List<KSDeclaration>>,
)

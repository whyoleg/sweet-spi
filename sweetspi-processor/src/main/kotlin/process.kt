/*
 * Copyright (c) 2024 Oleg Yukhnevich. Use of this source code is governed by the Apache 2.0 license.
 */

package dev.whyoleg.sweetspi.processor

import com.google.devtools.ksp.processing.*
import com.google.devtools.ksp.symbol.*

fun process(environment: SymbolProcessorEnvironment, resolver: Resolver): List<KSAnnotated> {
    // no need to process just common sources
    val platform = environment.platforms.singleOrNull() ?: return emptyList()
    val context = analyze(environment.logger, resolver) ?: return emptyList()
    generate(environment.codeGenerator, platform, context)
    return emptyList()
}

/*
 * Copyright (c) 2025 Oleg Yukhnevich. Use of this source code is governed by the Apache 2.0 license.
 */

package dev.whyoleg.sweetspi.compiler

import org.jetbrains.kotlin.compiler.plugin.*

@OptIn(ExperimentalCompilerApi::class)
class SweetCommandLineProcessor : CommandLineProcessor {
    override val pluginId: String = "dev.whyoleg.sweetspi"
    override val pluginOptions: Collection<CliOption> = emptyList()
}

/*
 * Copyright (c) 2025 Oleg Yukhnevich. Use of this source code is governed by the Apache 2.0 license.
 */

package dev.whyoleg.sweetspi.compiler

import org.jetbrains.kotlin.config.*
import java.nio.file.*

object SweetConfigurationKeys {
    val RESOURCES_PATH = CompilerConfigurationKey<Path>("Path to resources dir")
}

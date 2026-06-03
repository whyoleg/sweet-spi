/*
 * Copyright (c) 2026 Oleg Yukhnevich. Use of this source code is governed by the Apache 2.0 license.
 */

package dev.whyoleg.sweetspi.compiler.fir

import org.jetbrains.kotlin.*

object SweetGeneratedDeclarationKey : GeneratedDeclarationKey() {
    override fun toString(): String = "SweetSPI"
}

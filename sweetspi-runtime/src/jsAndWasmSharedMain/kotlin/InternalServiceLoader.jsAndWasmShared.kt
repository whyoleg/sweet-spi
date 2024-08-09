/*
 * Copyright (c) 2024 Oleg Yukhnevich. Use of this source code is governed by the Apache 2.0 license.
 */

package dev.whyoleg.sweetspi.internal

// IDEA shows an error because of a wrong visibility inspection
internal actual typealias SynchronizedObject = Any

internal actual inline fun <T> synchronized(lock: SynchronizedObject, block: () -> T): T = block()

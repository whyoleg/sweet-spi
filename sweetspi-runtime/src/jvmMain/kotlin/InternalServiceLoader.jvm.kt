/*
 * Copyright (c) 2024 Oleg Yukhnevich. Use of this source code is governed by the Apache 2.0 license.
 */

package dev.whyoleg.sweetspi.internal

import java.util.*

@JvmField
@InternalSweetSpiApi
internal actual val internalServiceLoader: Lazy<InternalServiceLoader> = lazy {
    InternalServiceLoader(ServiceLoader.load(InternalServiceModule::class.java).toList())
}

// IDEA shows an error because of a wrong visibility inspection
internal actual typealias SynchronizedObject = Any

internal actual inline fun <T> synchronized(lock: SynchronizedObject, block: () -> T): T = kotlin.synchronized(lock, block)

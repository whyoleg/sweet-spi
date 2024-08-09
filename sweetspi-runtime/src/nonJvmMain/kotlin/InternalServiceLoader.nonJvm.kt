/*
 * Copyright (c) 2024 Oleg Yukhnevich. Use of this source code is governed by the Apache 2.0 license.
 */

package dev.whyoleg.sweetspi.internal

@InternalSweetSpiApi
private val modules = mutableListOf<InternalServiceModule>()

@InternalSweetSpiApi
internal actual val internalServiceLoader: Lazy<InternalServiceLoader> = lazy { InternalServiceLoader(modules) }

@InternalSweetSpiApi
public fun registerInternalServiceModule(module: InternalServiceModule) {
    check(!internalServiceLoader.isInitialized()) { "ServiceLoader was already initialized, no more modules can be registered" }
    modules += module
}

/*
 * Copyright (c) 2024 Oleg Yukhnevich. Use of this source code is governed by the Apache 2.0 license.
 */

package dev.whyoleg.sweetspi.internal

@InternalSweetSpiApi
private val modules = mutableListOf<InternalServiceModule>()

@InternalSweetSpiApi
public fun registerInternalServiceModule(module: InternalServiceModule) {
    modules += module
}

@InternalSweetSpiApi
internal actual fun getAvailableModules(): List<InternalServiceModule> = modules
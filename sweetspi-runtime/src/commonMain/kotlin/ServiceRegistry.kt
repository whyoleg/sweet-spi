/*
 * Copyright (c) 2025 Oleg Yukhnevich. Use of this source code is governed by the Apache 2.0 license.
 */

package dev.whyoleg.sweetspi

import kotlin.reflect.*

public interface ServiceRegistry : ServiceLoader {
    // declare, that it's possible to use the service, validation is performed lazy on load
    public fun <T : Any> registerService(cls: KClass<T>)
    public fun <T : Any> registerServiceProvider(cls: KClass<T>, provider: () -> T)
}

public inline fun <reified T : Any> ServiceRegistry.registerService(): Unit = registerService(T::class)

public inline fun <reified T : Any> ServiceRegistry.registerServiceProvider(noinline provider: () -> T): Unit =
    registerServiceProvider(T::class, provider)

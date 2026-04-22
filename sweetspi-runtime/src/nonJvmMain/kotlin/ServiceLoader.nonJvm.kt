/*
 * Copyright (c) 2026 Oleg Yukhnevich. Use of this source code is governed by the Apache 2.0 license.
 */

package dev.whyoleg.sweetspi

import kotlin.jvm.*
import kotlin.reflect.*

public actual object ServiceLoader {
    public actual inline fun <reified T : Any> load(): Sequence<T> = load(T::class)
    public actual inline fun <reified T : Any> loadProviders(): Sequence<Provider<T>> = loadProviders(T::class)

    @JvmStatic
    public actual fun <T : Any> load(cls: KClass<T>): Sequence<T> = TODO()

    @JvmStatic
    public actual fun <T : Any> loadProviders(cls: KClass<T>): Sequence<Provider<T>> = TODO()

    public actual interface Provider<T : Any> : Lazy<T> {
        public actual val type: KClass<out T>
    }
}

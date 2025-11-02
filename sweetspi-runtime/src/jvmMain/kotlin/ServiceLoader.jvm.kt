/*
 * Copyright (c) 2025 Oleg Yukhnevich. Use of this source code is governed by the Apache 2.0 license.
 */

package dev.whyoleg.sweetspi.internal

import dev.whyoleg.sweetspi.*
import kotlin.reflect.*


/**
 * Retrieves a list of services of the specified type [T], which must be annotated with [Service].
 * Providers of these services must be annotated with [ServiceProvider].
 */
@OptIn(InternalSweetSpiApi::class)
public fun <T : Any> ServiceLoader.load(cls: KClass<T>, classLoader: ClassLoader, reloadProviders: Boolean = false): List<T> =
    InternalServiceLoader.load(cls, classLoader, reloadProviders)

/**
 * Retrieves a list of services of the specified type [T], which must be annotated with [Service].
 * Providers of these services must be annotated with [ServiceProvider].
 */
public inline fun <reified T : Any> ServiceLoader.load(classLoader: ClassLoader, reloadProviders: Boolean = false): List<T> =
    load(T::class, classLoader, reloadProviders)
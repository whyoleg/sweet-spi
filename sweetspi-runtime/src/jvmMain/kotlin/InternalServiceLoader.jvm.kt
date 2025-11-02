/*
 * Copyright (c) 2024 Oleg Yukhnevich. Use of this source code is governed by the Apache 2.0 license.
 */

package dev.whyoleg.sweetspi.internal

import java.util.*
import kotlin.reflect.*

// IDEA shows an error because of a wrong visibility inspection
internal actual typealias SynchronizedObject = Any

internal actual inline fun <T> synchronized(lock: SynchronizedObject, block: () -> T): T = kotlin.synchronized(lock, block)

@InternalSweetSpiApi
internal actual fun getAvailableModules(): List<InternalServiceModule> = Iterable {
    // ServiceLoader should use specific call convention to be optimized by R8 on Android:
    // `ServiceLoader.load(X.class, X.class.getClassLoader()).iterator()`
    // source:
    // https://r8.googlesource.com/r8/+/refs/heads/main/src/main/java/com/android/tools/r8/ir/optimize/ServiceLoaderRewriter.java
    ServiceLoader.load(
        InternalServiceModule::class.java,
        InternalServiceModule::class.java.classLoader
    ).iterator()
}.toList()

@InternalSweetSpiApi
internal fun getAvailableModules(classLoader: ClassLoader): List<InternalServiceModule> = Iterable {
    ServiceLoader.load(
        InternalServiceModule::class.java,
        classLoader
    ).iterator()
}.toList()

@InternalSweetSpiApi
internal fun <T : Any> InternalServiceLoader.load(cls: KClass<T>, classLoader: ClassLoader, reloadProviders: Boolean = false): List<T> {
    return load(getAvailableModules(classLoader), cls, reloadProviders)
}
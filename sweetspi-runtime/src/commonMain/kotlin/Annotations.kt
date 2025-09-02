/*
 * Copyright (c) 2025 Oleg Yukhnevich. Use of this source code is governed by the Apache 2.0 license.
 */

package dev.whyoleg.sweetspi

import kotlin.reflect.*

@MustBeDocumented
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.BINARY)
public annotation class Pluggable

@Repeatable
@MustBeDocumented
@Target(AnnotationTarget.CLASS, AnnotationTarget.PROPERTY, AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.BINARY)
public annotation class Plugin(public val pluggable: KClass<*>)

// like SerialInfo in kotlinx.serialization
@MustBeDocumented
@Target(AnnotationTarget.ANNOTATION_CLASS)
@Retention(AnnotationRetention.BINARY)
public annotation class PluginInfo

/**
 * ```
 * // module: A
 * @JvmService // enables compatibility with java.util.ServiceLoader
 * @Pluggable
 * interface CryptographyProvider
 * @Pluggable interface LoggingBackend
 *
 * // module: A, B or even in a library!
 * @Plugin(CryptographyProvider::class) object OpensslCryptographyProvider : CryptographyProvider
 * @Plugin(LoggingBackend::class) object Log4jBackend : LoggingBackend
 *
 * // module: A, B, C or may be not even in your codebase...
 * fun main() {
 *     PluginLoader<CryptographyProvider>().getAll() // List
 *     PluginLoader<LoggingBackend>().getAll() // List
 *     PluginSource<LoggingBackend>(listOf(Log4jBackend)) // list/sequence/iterable
 *
 *     val registry = PluginRegistry<CryptographyProvider>()
 *     registry.register { CryptographyProviderImpl }
 *     registry.getAll() // List
 * }
 * ```
 */
private fun test() {
    PluginLoader<String>().getSingle()
    PluginLoader<String>().getAll().forEach {

    }
    val s = PluginSource<String>(
        sequence<String> {
            yield("123")
        }
    )

    PluginRegistry<String>().apply {
        register(
            annotations = listOf(
                PluginPriority(123)
            ),
            provider = { "hello" }
        )
    }
}

public annotation class PluginPriority(public val priority: Int)

//
////// something similar to CryptographySystemImpl
//public class ServiceBootstrap<T : Any>(
//    source: PluginSource<T>,
//// some functional interface / lambda which decides how to select default service
//// option to provide ServiceLocator
//) {
//    // can be set until queried
//    public var default: T = TODO()
//    public val registered: List<T> get() = TODO()
//    public fun register(provider: () -> T) {}
//}

private fun tests() {
    PluginSource {
        yieldFrom(PluginLoader<String>())
        yieldFrom(PluginRegistry())
    }
}

public class PluginBootstrap<T : Any>(defaultValue: () -> T) {
    private var value: T? = null
    private val lazyValue = lazy { value ?: defaultValue() }

    public fun get(): T = lazyValue.value

    public fun set(value: T) {
        check(!lazyValue.isInitialized()) { "Cannot set value after `get` was called" }
        check(this.value == null) { "Value is already set" }

        this.value = value
    }

    public fun trySet(value: T): Boolean {
        if (lazyValue.isInitialized()) return false
        if (this.value != null) return false

        this.value = value
        return true
    }
}

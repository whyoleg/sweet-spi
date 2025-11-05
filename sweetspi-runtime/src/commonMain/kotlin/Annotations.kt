/*
 * Copyright (c) 2025 Oleg Yukhnevich. Use of this source code is governed by the Apache 2.0 license.
 */

package dev.whyoleg.sweetspi

import kotlin.reflect.*

@MustBeDocumented
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.BINARY)
public annotation class Pluggable {
    public interface Provider<T : Any> {
        public fun provide(): T
    }
}

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
 * In case of JVM, we do have information about KClass (and annotation) of the `Plugin`
 * In case of Wasm/Js/etc, we COULD have an some metadata stored somewhere, before we will try to load implementation,
 *   but the loading will happen in an `async` way, and so it will probably be `interface FleetPlugin { suspend fun loadPlugin() }` and so
 *   this `FleetPlugin` instances can have metadata
 *   It feels like having `PluginInfo` is really not useful...
 *
 */

// from where could plugins in the case of Wasm/JS/browser come?

@Pluggable
public interface LoggingBackend {
    // generated to support functions/properties/objects on JVM
    // overall, might not be needed, when we are using JPMS
    public interface Provider : Pluggable.Provider<LoggingBackend>
//
//    public companion object : PluggableBootstrap<LoggingBackend> by PluggableBootstrap.pluginLoader<LoggingBackend>() {
//        public val Default: LoggingBackend get() = TODO()
//        public fun bootstrap(value: LoggingBackend) {}
//    }
}

// for companion object if single instance is used afterwards - can be abstract class to have logic - to api dependent
public interface PluggableBootstrap<T : Any> {
    public val Default: T
    public fun bootstrap(value: T)
}

// automatically loaded
@Plugin(LoggingBackend::class)
public object LoggingBackendImpl : LoggingBackend

// as in kx.serialization
public fun LoggingBackend.Companion.pluginLoader(): PluginLoader<LoggingBackend> = TODO()

public fun LoggingBackend.Companion.load(): Sequence<LoggingBackend> = TODO()

// for JVM
public fun LoggingBackend.Companion.load(classpath: String): Sequence<LoggingBackend> = TODO()

// generated
public interface Pluggable_LoggingBackend : Pluggable.Provider<LoggingBackend>

/**
 * ```
 * // module: A
 * @JvmService // enables compatibility with java.util.ServiceLoader
 * @Pluggable
 * interface CryptographyProvider
 * @Pluggable interface LoggingBackend
 *
 * // generates if without
 * interface Pluggable_LoggingBackend: Pluggable.Provider<LoggingBackend>
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

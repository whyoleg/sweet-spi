/*
 * Copyright (c) 2024-2025 Oleg Yukhnevich. Use of this source code is governed by the Apache 2.0 license.
 */

package dev.whyoleg.sweetspi

import kotlin.reflect.*

/**
 * Provides functionality for dynamically loading service implementations using the Service Provider Interface (SPI) mechanism.
 *
 * It works in conjunction with [Service] and [ServiceProvider] annotations:
 * - interfaces or abstract classes annotated with [Service] can be retrieved using this loader.
 * - implementations of these services are identified using the [ServiceProvider] annotation.
 *
 * Usage:
 * ```
 * // module: A
 * @Service
 * interface SimpleService {
 *     fun saySomethingSweet()
 * }
 *
 * // module: A, B or even in a library!
 * @ServiceProvider
 * object SimpleServiceImpl : SimpleService {
 *     override fun saySomethingSweet() {
 *         println("Kotlin is Awesome")
 *     }
 * }
 *
 * // module: A, B, C or may be not even in your codebase...
 * fun main() {
 *     ServiceLoader.loadAll<SimpleService>().forEach { service ->
 *         service.saySomethingSweet()
 *     }
 * }
 * ```
 */
public interface ServiceLoader {
    public fun <T : Any> loadServices(cls: KClass<T>): Sequence<T>

    // intrinsic candidates on JVM (may be just `reified` calls)
    // it should work even without CP, but could be slower
    // CP should validate that the class is annotated with `@Service`?
    public companion object Default : ServiceLoader {
        override fun <T : Any> loadServices(cls: KClass<T>): Sequence<T> = DefaultServiceLoader.loadServices(cls)
        public inline fun <reified T : Any> loadServices(): Sequence<T> = loadServices(T::class)
    }
}

public inline fun <reified T : Any> ServiceLoader.loadServices(): Sequence<T> = loadServices(T::class)

internal expect val DefaultServiceLoader: ServiceLoader

// ServiceLoader should use specific call convention to be optimized by R8 on Android:
// `ServiceLoader.load(X.class, X.class.getClassLoader()).iterator()`
// source:
// https://r8.googlesource.com/r8/+/refs/heads/main/src/main/java/com/android/tools/r8/ir/optimize/ServiceLoaderRewriter.java
// JVM intrinsic should generate for `X`
//public interface X
//@PublishedApi internal interface X_Provider {
//    public fun get(): X
//}
//object XImpl: X
//class XImpl_Provider: X_Provider {
// override fun invoke(): X = XImpl
//}
//
//// for @JvmService
//private fun generated() {
//    Sequence {
//        JServiceLoader.load(X::class.java, X::class.java.classLoader).iterator()
//    }
//}
//
//// @Service
//private fun generatedProvider() {
//    Sequence {
//        JServiceLoader.load(X_Provider::class.java, X_Provider::class.java.classLoader).iterator()
//    }.map { it.get() }
//}

// for klib:
//@ServiceInfo
//public annotation class Priority(val value: Int)
//
//public interface LoggerFactory
//
//@Priority(123)
//public object LoggerFactoryImpl : LoggerFactory
//
//// generated
//@OptIn(InternalSweetSpiApi::class)
//private val init = with(InternalServiceLoader) {
//    registerService(LoggerFactory::class) // for validation
//    registerInitializer(LoggerFactory::class, LoggerFactoryImpl_Initializer) // for initialization
//}

//// generated
//private val LoggerFactory$init = InternalServiceRegistry.registerService(LoggerFactory::class) // for validation
//private val LoggerFactoryImpl$init = InternalServiceRegistry.registerInitializer(LoggerFactory::class) { LoggerFactoryImpl }  // for initialization
//
//// generated
//private object LoggerFactoryImpl_Initializer : ServiceInitializer<LoggerFactory> {
//    override val annotations: List<Annotation> get() = listOf(Priority(123))
//    override fun initialize(): LoggerFactory = LoggerFactoryImpl
//}

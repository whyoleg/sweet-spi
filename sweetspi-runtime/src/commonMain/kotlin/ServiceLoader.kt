/*
 * Copyright (c) 2024 Oleg Yukhnevich. Use of this source code is governed by the Apache 2.0 license.
 */

package dev.whyoleg.sweetspi

import kotlin.reflect.*

/**
 * This annotation is used to indicate that a class is a service which could be provided via [ServiceLoader.load]
 *
 * Implementations of these services are identified by [Provides] annotation.
 *
 * This annotation could be applied only to **interfaces** or **abstract classes**.
 *
 * Usage:
 * ```
 * @Service
 * interface SimpleService {
 *     fun saySomethingSweet()
 * }
 * ```
 *
 * #### JVM
 *
 * Depending on [javaServiceLoaderCompatibilityMode]:
 * - [JavaServiceLoaderCompatibilityMode.NO_COMPATIBILITY]: the service should be loaded **only** via [ServiceLoader.load].
 *     Could be provided by Kotlin consumers only.
 *     (single call to JSL: X$ServiceProvider)
 *     [Provides] will use X$ServiceProvider.
 * - [JavaServiceLoaderCompatibilityMode.PROVIDE_COMPATIBILITY]: the service should be loaded **only** via [ServiceLoader.load].
 *     Could be provided by both Java and Kotlin consumers.
 *     (two calls to JSL: one with X and one with X$ServiceProvider).
 *     [Provides] will use X$ServiceProvider.
 * - [JavaServiceLoaderCompatibilityMode.FULL_COMPATIBILITY]: the service could be loaded by both `java.util.ServiceLoader` and [ServiceLoader.load].
 *     Could be provided by both Java and Kotlin consumers.
 *     (single call to JSL: X).
 *     [Provides] will use X
 *     Not all Kotlin features will be supported, like providing via objects or functions without JPMS or ServiceInfo.
 */
@MustBeDocumented
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME) // runtime needed for JVM only
public annotation class Service(
    val javaServiceLoaderCompatibilityMode: JavaServiceLoaderCompatibilityMode = JavaServiceLoaderCompatibilityMode.NO_COMPATIBILITY,
)

public enum class JavaServiceLoaderCompatibilityMode {
    NO_COMPATIBILITY,
    PROVIDE_COMPATIBILITY,
    FULL_COMPATIBILITY,
}

/**
 * This annotation is used to identify which service the annotated element provides an instance for.
 *
 * This annotation can be applied to the following targets:
 * - [AnnotationTarget.CLASS]: Applicable to objects or classes with:
 *   1. No explicit constructor, or
 *   2. A constructor that has no parameters, or
 *   3. A constructor where all parameters have default values.
 * - [AnnotationTarget.PROPERTY]: Only applicable to immutable non-suspend properties with getter or initializer
 * - [AnnotationTarget.FUNCTION]: Only applicable to non-suspend functions without arguments and without receiver
 *
 * Usage:
 * ```
 * @Provides(SimpleService::class)
 * object SimpleServiceImpl : SimpleService {
 *     override fun saySomethingSweet() { ... }
 * }
 * ```
 */
@Repeatable
@MustBeDocumented
@Target(AnnotationTarget.CLASS, AnnotationTarget.PROPERTY, AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.BINARY)
public annotation class Provides(public val service: KClass<*>)

// allows providing services without `@Service` annotation - just for compatibility
// no checks, just META-INF generation
// error if interface has `@Service` annotation
@Repeatable
@MustBeDocumented
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.BINARY)
public annotation class JvmProvides(public val service: KClass<*>)

/**
 * Provides functionality for dynamically loading service implementations using the Service Provider Interface (SPI) mechanism.
 *
 * It works in conjunction with [Service] and [Provides] annotations:
 * - interfaces or abstract classes annotated with [Service] can be retrieved using this loader.
 * - implementations of these services are identified using the [Provides] annotation.
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
 * @Provides(SimpleService::class)
 * object SimpleServiceImpl : SimpleService {
 *     override fun saySomethingSweet() {
 *         println("Kotlin is Awesome")
 *     }
 * }
 *
 * // module: A, B, C or may be not even in your codebase...
 * fun main() {
 *     ServiceLoader.load<SimpleService>().forEach { provider ->
 *         provider.value.saySomethingSweet()
 *     }
 * }
 * ```
 */
public expect open class ServiceLoader {
    public companion object Default : ServiceLoader

    /**
     * Retrieves a list of services of the specified type [T], which must be annotated with [Service].
     * Providers of these services must be annotated with [Provides].
     *
     * Returned sequence can be iterated only once.
     *
     * On JVM: optimizable by R8
     */
    public inline fun <reified T : Any> load(): Sequence<ServiceProvider<T>>

    /**
     * Retrieves a list of services of the specified type [T], which must be annotated with [Service].
     * Providers of these services must be annotated with [Provides].
     *
     * Returned sequence can be iterated only once.
     */
    public fun <T : Any> load(type: KClass<T>): Sequence<ServiceProvider<T>>

    // TODO: this might be some other API really - `@Service` should declare, single or multiple implementations are expected or not
    // TODO: add `loadOne(): T | NoServiceProvider | MultipleServiceProviders` that could be optimized for cases, when single implementation is expected
    //  it will be like optional expect/actual
    //  build should enforce, that only single implementation is expected
    //  no need to have `ServiceProvider`
}

// same as in JDK, same as in a lot of DIs, where Provider<T> = () -> T
public interface ServiceProvider<T : Any> : Lazy<T> {
    // type of the real instance (or type of the method returning an instance)
    // TODO: is it useful really? if we will have `@ServiceInfo`, we could also add those annotation to functions/properties of providers.
    // in reality, users should not care about "implementation" details, but only about some additional metadata (like `@ServiceInfo`) that can be used for introspection or debugging.
    public val type: KClass<out T>
}

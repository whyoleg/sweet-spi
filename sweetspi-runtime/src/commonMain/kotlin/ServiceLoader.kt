/*
 * Copyright (c) 2024 Oleg Yukhnevich. Use of this source code is governed by the Apache 2.0 license.
 */

package dev.whyoleg.sweetspi

import kotlin.jvm.*
import kotlin.reflect.*

/**
 * This annotation is used to indicate that a class is a service which could be provided via [ServiceLoader.load]
 *
 * Implementations of these services are identified by [ServiceProvider] annotation.
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
 * Depending on [compatibleWithJavaServiceLoader]:
 * - `false`: the service could be loaded **only** via [ServiceLoader.load].
 * - `true`: the service could be loaded via `java.util.ServiceLoader`.
 *   In this case, some Kotlin features will not be available;
 *   for example, the service could not be [provided][ServiceProvider] via an `object`, `function`, or `property`.
 */
@MustBeDocumented
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME) // runtime needed for JVM only
public annotation class Service(
    val compatibleWithJavaServiceLoader: Boolean = false,
)

/**
 * This annotation is used to identify which service(s) the annotated element provides an instance for.
 *
 * If no [services] are explicitly provided, the plugin will attempt to find all supertypes of the element
 * that have the [Service] annotation.
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
 * @ServiceProvider(SimpleService::class)
 * object SimpleServiceImpl : SimpleService {
 *     override fun saySomethingSweet() { ... }
 * }
 * ```
 *
 * #### JVM
 *
 * Depending on [allowsUnannotatedJavaServices]:
 * - `false`: [services] should be annotated with [Service] annotation
 * - `true`: allows providing a [services] without [Service] annotation; no compile-time validation will be available; jvm only
 */
@MustBeDocumented
@Target(AnnotationTarget.CLASS, AnnotationTarget.PROPERTY, AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.BINARY)
public annotation class ServiceProvider(
    public vararg val services: KClass<*>,
    val allowsUnannotatedJavaServices: Boolean = false,
)

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
 *     ServiceLoader.load<SimpleService>().forEach { service ->
 *         service.saySomethingSweet()
 *     }
 * }
 * ```
 */
public expect object ServiceLoader {
    /**
     * Retrieves a list of services of the specified type [T], which must be annotated with [Service].
     * Providers of these services must be annotated with [ServiceProvider].
     *
     * Returned sequence can be iterated only once.
     *
     * On JVM: optimizable by R8
     */
    public inline fun <reified T : Any> load(): Sequence<T>

    /**
     * Retrieves a list of [providers][Provider] for services of the specified type [T], which must be annotated with [Service].
     * Providers of these services must be annotated with [ServiceProvider].
     *
     * Returned sequence can be iterated only once.
     * [Provider.value] instantiation is lazy on the best effort basis.
     *
     * On JVM: optimizable by R8
     */
    public inline fun <reified T : Any> loadProviders(): Sequence<Provider<T>>

    /**
     * Retrieves a list of services of the specified type [T], which must be annotated with [Service].
     * Providers of these services must be annotated with [ServiceProvider].
     *
     * Returned sequence can be iterated only once.
     */
    @JvmStatic
    public fun <T : Any> load(cls: KClass<T>): Sequence<T>

    /**
     * Retrieves a list of [providers][Provider] for services of the specified type [T], which must be annotated with [Service].
     * Providers of these services must be annotated with [ServiceProvider].
     *
     * Returned sequence can be iterated only once.
     * [Provider.value] instantiation is lazy on the best effort basis.
     */
    @JvmStatic
    public fun <T : Any> loadProviders(cls: KClass<T>): Sequence<Provider<T>>

    public interface Provider<T : Any> : Lazy<T> {
        // type of the real instance (or type of the method returning an instance)
        public val type: KClass<out T>
    }
}

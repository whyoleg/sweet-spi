/*
 * Copyright (c) 2024 Oleg Yukhnevich. Use of this source code is governed by the Apache 2.0 license.
 */

@file:Suppress("PackageDirectoryMismatch")

package dev.whyoleg.sweetspi

import dev.whyoleg.sweetspi.ServiceLoader.load
import dev.whyoleg.sweetspi.internal.*
import kotlin.jvm.*
import kotlin.reflect.*

/**
 * This annotation is used to indicate that a class is a service which could be provided via [ServiceLoader.load]
 * Implementations of these services are identified by [ServiceProvider] annotation.
 * This annotation could be applied only to interfaces or abstract classes.
 *
 * Example of usage:
 * ```
 * @Service
 * interface SimpleService {
 *     fun saySomethingSweet()
 * }
 * ```
 */
@MustBeDocumented
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.BINARY)
public annotation class Service

/**
 * This annotation is used to identify which service(s) the annotated element provides an instance for.
 * If no [services] are explicitly provided, the plugin will attempt to find all supertypes of the element
 * that have the [Service] annotation.
 *
 * This annotation can be applied to the following targets:
 * - [AnnotationTarget.CLASS]: Only applicable to objects
 * - [AnnotationTarget.PROPERTY]: Only applicable to immutable non-suspend properties with getter or initializer
 * - [AnnotationTarget.FUNCTION]: Only applicable to non-suspend functions without arguments and without receiver
 *
 * Example of usage:
 * ```
 * @ServiceProvider(SimpleService::class)
 * object SimpleServiceImpl : SimpleService {
 *     override fun saySomethingSweet() { ... }
 * }
 * ```
 */
@MustBeDocumented
@Target(AnnotationTarget.CLASS, AnnotationTarget.PROPERTY, AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.BINARY)
public annotation class ServiceProvider(
    public vararg val services: KClass<*>,
)

/**
 * Provides functionality for dynamically loading service implementations using the Service Provider Interface (SPI) mechanism.
 * This allows for the flexible and dynamic discovery and instantiation of service providers.
 * It works in conjunction with [Service] and [ServiceProvider] annotations:
 * - interfaces or abstract classes annotated with [Service] can be retrieved using this loader.
 * - implementations of these services are identified using the [ServiceProvider] annotation.
 *
 * Example of usage:
 *
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
public object ServiceLoader {
    /**
     * Retrieves a list of services of the specified type [T], which must be annotated with [Service].
     * Providers of these services must be annotated with [ServiceProvider].
     *
     * @param cls The class of the service to retrieve
     * @return A list of services of the specified type [T] that were discovered and instantiated
     */
    @JvmStatic
    @OptIn(InternalSweetSpiApi::class)
    public fun <T : Any> load(cls: KClass<T>): List<T> = internalServiceLoader.value.load(cls)

    /**
     * Retrieves a list of services of the specified type [T], which must be annotated with [Service].
     * Providers of these services must be annotated with [ServiceProvider].
     *
     * @return A list of services of the specified type [T] that were discovered and instantiated
     */
    public inline fun <reified T : Any> ServiceLoader.load(): List<T> = load(T::class)
}

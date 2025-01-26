/*
 * Copyright (c) 2025 Oleg Yukhnevich. Use of this source code is governed by the Apache 2.0 license.
 */

package dev.whyoleg.sweetspi

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
 */
@MustBeDocumented
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
public annotation class Service

/**
 * This annotation is used to identify which service(s) the annotated element provides an instance for.
 *
 * If no [services] are explicitly provided, the plugin will attempt to find all supertypes of the element
 * that have the [Service] annotation.
 *
 * This annotation can be applied to the following targets:
 * - [AnnotationTarget.CLASS]: Only applicable to objects
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
 */
@MustBeDocumented
@Target(AnnotationTarget.CLASS, AnnotationTarget.PROPERTY, AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.BINARY)
public annotation class ServiceProvider(public vararg val services: KClass<*>)

// TODO: implement JVM interop (find better names)
// if the annotation is present, then there will be restrictions on how it could be used by ServiceProvider
@MustBeDocumented
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
public annotation class JvmService

// to be interoperable with the jvm service loader
// doesn't require to have `Service` annotation
@Repeatable
@MustBeDocumented
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.BINARY)
public annotation class JvmServiceProvider(public val service: KClass<*>)

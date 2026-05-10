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
 * Depending on [compatibleWithJavaServiceLoader]:
 * - `false`: the service could be loaded **only** via [ServiceLoader.load].
 * - `true`: the service could be loaded via `java.util.ServiceLoader`.
 *   In this case, some Kotlin features will not be available;
 *   for example, the service could not be [provided][Provides] via an `object`, `function`, or `property`.
 */
@MustBeDocumented
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME) // runtime needed for JVM only
public annotation class Service(val compatibleWithJavaServiceLoader: Boolean = false)

/**
 * This annotation is used to identify which service(s) the annotated element provides an instance for.
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

// same as in JDK, same as in a lot of DIs, where Provider<T> = () -> T
public interface ServiceProvider<T : Any> : Lazy<T> {
    // type of the real instance (or type of the method returning an instance)
    public val type: KClass<out T>
}

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
}

// test usage
//@Service
//public interface X {
//    public interface `$ServiceProvider` : ServiceProvider<X>
//}
//
//@Service
//public interface Y {
//    public interface `$ServiceProvider` : ServiceProvider<Y>
//}
//
//@Provides(X::class)
//@Provides(Y::class)
//public object XY_Impl : X, Y {
//    internal class `$dev_whyoleg_sweetspi_X$ServiceProvider` : X.`$ServiceProvider` {
//        override val type: KClass<out X>
//            get() = XY_Impl::class
//
//        // delegate should be generated in a body: in case `XY_Impl` is a class, we should create new instance of a class every time we call `load`
//        // this is needed to mimic Java ServiceLoader behavior
//        private val delegate = lazy { XY_Impl }
//        override val value: X get() = delegate.value
//        override fun isInitialized(): Boolean = delegate.isInitialized()
//    }
//
//    internal class `$Provider$dev_whyoleg_sweetspi_Y` : Y.`$Provider` {
//        override val type: KClass<out Y>
//            get() = XY_Impl::class
//
//        // delegate should be generated in a body: in case `XY_Impl` is a class, we should create new instance of a class every time we call `load`
//        // this is needed to mimic Java ServiceLoader behavior
//        private val delegate = lazy { XY_Impl }
//        override val value: Y get() = delegate.value
//        override fun isInitialized(): Boolean = delegate.isInitialized()
//    }
//}

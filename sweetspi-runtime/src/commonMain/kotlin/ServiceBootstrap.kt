/*
 * Copyright (c) 2025 Oleg Yukhnevich. Use of this source code is governed by the Apache 2.0 license.
 */

package dev.whyoleg.sweetspi

/**
 * Represents a mechanism for bootstrapping or initializing a service of type [T].
 * This interface allows defining a custom initialization strategy or setup for a specific service.
 *
 * Typically intended for use within companion objects or similar static contexts to define
 * the entry point for service initialization logic.
 *
 * @param T The type of the service to be bootstrapped.
 */
// should be used mostly by companion objects
public interface ServiceBootstrap<T : Any> {
    /**
     * Registers a provider function to initialize a service instance of type [T].
     *
     * This method allows a service implementation to be dynamically bootstrapped by providing
     * a supplier function that will be invoked to create the service instance when needed.
     *
     * @param value A lambda function that returns an instance of the service type [T].
     */
    // fails if it was already initialized
    public fun bootstrap(value: () -> T)

    // returns true, if it was successfully initialized
    public fun tryBootstrap(value: () -> T): Boolean
}

public interface LazyServiceValue<T : Any> : Lazy<T>, ServiceBootstrap<T>

/**
 * Provides a utility function to bootstrap a service using a pre-created instance.
 *
 * This function allows you to directly pass an instance of a service to be used as the implementation
 * of the specified service type within the `ServiceBootstrap` mechanism.
 *
 * @param value The instance of the service to be used for bootstrapping.
 */
@Suppress("NOTHING_TO_INLINE")
public inline fun <T : Any> ServiceBootstrap<T>.bootstrap(value: T): Unit = bootstrap { value }

@Suppress("NOTHING_TO_INLINE")
public inline fun <T : Any> ServiceBootstrap<T>.tryBootstrap(value: T): Boolean = tryBootstrap { value }

// TODO: guard with lock and may be optimize a bit
public abstract class DefaultServiceBootstrap<T : Any>(
    private val defaultValue: () -> T,
) : ServiceBootstrap<T> {
    // 3 states: NOTHING (lambda), SET (lambda), INITIALIZED (value)
    private var bootstrapValueProvider: (() -> T)? = null
    private val lazyValue = lazy { bootstrapValueProvider?.invoke() ?: defaultValue.invoke() }
    protected fun accessValue(): T = lazyValue.value

    public override fun bootstrap(value: () -> T) {
        check(bootstrapValueProvider == null) { "${this::class.simpleName} has been already bootstrapped" }
        check(!lazyValue.isInitialized()) { "${this::class.simpleName} value has been already accessed" }

        bootstrapValueProvider = value
    }

    public override fun tryBootstrap(value: () -> T): Boolean {
        if (bootstrapValueProvider != null) return false
        if (lazyValue.isInitialized()) return false

        bootstrapValueProvider = value
        return true
    }
}

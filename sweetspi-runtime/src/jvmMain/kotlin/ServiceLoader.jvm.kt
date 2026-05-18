/*
 * Copyright (c) 2026 Oleg Yukhnevich. Use of this source code is governed by the Apache 2.0 license.
 */

package dev.whyoleg.sweetspi

import kotlin.reflect.*
import kotlin.streams.*
import java.util.ServiceLoader as JavaServiceLoader

// ModuleLayer and JSL provider are JDK9+

public actual open class ServiceLoader private constructor(
    private val classLoader: ClassLoader?,
    private val moduleLayer: ModuleLayer?, // TODO: check if this works fine
) {
    public actual companion object Default : ServiceLoader(null, null)

    public constructor(classLoader: ClassLoader) : this(classLoader, null)
    public constructor(moduleLayer: ModuleLayer) : this(null, moduleLayer)

    public actual inline fun <reified T : Any> load(): Sequence<ServiceProvider<T>> = load(T::class)
    public actual fun <T : Any> load(type: KClass<T>): Sequence<ServiceProvider<T>> = load(type.java)
    public fun <T : Any> load(type: Class<T>): Sequence<ServiceProvider<T>> = loadImpl(type)

    // impl
    private val hasServiceLoaderProvider: Boolean = try {
        Class.forName($$"java.util.ServiceLoader$Provider")
        true
    } catch (_: Throwable) {
        false
    }

    private fun <T : Any> loadImpl(cls: Class<T>): Sequence<ServiceProvider<T>> = when (javaServiceLoaderCompatibilityMode(cls)) {
        JavaServiceLoaderCompatibilityMode.NO_COMPATIBILITY         -> loadKotlinSequence(cls)
        JavaServiceLoaderCompatibilityMode.FULL_COMPATIBILITY, null -> loadJavaSequence(cls)
        JavaServiceLoaderCompatibilityMode.PROVIDE_COMPATIBILITY    -> loadKotlinSequence(cls) + loadJavaSequence(cls)
    }

    private fun <T : Any> loadKotlinSequence(cls: Class<T>): Sequence<ServiceProvider<T>> = loadSequence(getProviderClass(cls))
    private fun <T : Any> loadJavaSequence(cls: Class<T>): Sequence<ServiceProvider<T>> = when {
        hasServiceLoaderProvider -> loadProvidersSequence(cls).map(::JavaProviderWrapper)
        else                     -> loadSequence(cls).map(::InstanceProviderWrapper)
    }

    private fun <T : Any> loadSequence(cls: Class<T>): Sequence<T> {
        return loader(cls).iterator().asSequence()
    }

    private fun <T : Any> loadProvidersSequence(cls: Class<T>): Sequence<JavaServiceLoader.Provider<T>> {
        return loader(cls).stream().asSequence().constrainOnce()
    }

    private fun <T> loader(cls: Class<T>): JavaServiceLoader<T> = when {
        classLoader != null -> JavaServiceLoader.load(cls, classLoader)
        moduleLayer != null -> JavaServiceLoader.load(moduleLayer, cls)
        else                -> JavaServiceLoader.load(cls, cls.classLoader)
    }

    private fun <T : Any> javaServiceLoaderCompatibilityMode(cls: Class<T>): JavaServiceLoaderCompatibilityMode? {
        return cls.getAnnotation(Service::class.java)?.javaServiceLoaderCompatibilityMode
    }

    private fun <T : Any> getProviderClass(cls: Class<T>): Class<out ServiceProvider<T>> {
        val providerCls = cls.declaredClasses.firstOrNull { it.simpleName == $$"$ServiceProvider" }
            ?: error("Cannot find provider class for $cls")
        @Suppress("UNCHECKED_CAST")
        return providerCls as Class<out ServiceProvider<T>>
    }

    private class JavaProviderWrapper<T : Any>(
        private val provider: JavaServiceLoader.Provider<T>,
    ) : ServiceProvider<T> {
        override val type: KClass<out T> get() = provider.type().kotlin
        private val delegate = lazy(provider::get)
        override val value: T get() = delegate.value
        override fun isInitialized(): Boolean = delegate.isInitialized()
    }

    private class InstanceProviderWrapper<T : Any>(
        override val value: T,
    ) : ServiceProvider<T> {
        override val type: KClass<out T> get() = value::class
        override fun isInitialized(): Boolean = true
    }
}

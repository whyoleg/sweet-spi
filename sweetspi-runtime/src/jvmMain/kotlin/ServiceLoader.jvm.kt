/*
 * Copyright (c) 2026 Oleg Yukhnevich. Use of this source code is governed by the Apache 2.0 license.
 */

package dev.whyoleg.sweetspi

import kotlin.reflect.*
import kotlin.streams.*
import java.util.ServiceLoader as JavaServiceLoader

public actual object ServiceLoader {
    public actual inline fun <reified T : Any> load(): Sequence<T> = load(T::class)
    public actual inline fun <reified T : Any> loadProviders(): Sequence<Provider<T>> = loadProviders(T::class)

    @JvmStatic
    public actual fun <T : Any> load(cls: KClass<T>): Sequence<T> = load(cls.java, cls.java.classLoader)

    @JvmStatic
    public actual fun <T : Any> loadProviders(cls: KClass<T>): Sequence<Provider<T>> = loadProviders(cls.java, cls.java.classLoader)

    // KClass and ClassLoader and ModuleLayer overloads

    @JvmStatic
    public fun <T : Any> load(cls: KClass<T>, classLoader: ClassLoader): Sequence<T> = load(cls.java, classLoader)

    @JvmStatic
    public fun <T : Any> loadProviders(cls: KClass<T>, classLoader: ClassLoader): Sequence<Provider<T>> =
        loadProviders(cls.java, classLoader)

    @JvmStatic // JDK 9+
    public fun <T : Any> load(cls: KClass<T>, moduleLayer: ModuleLayer): Sequence<T> = load(cls.java, moduleLayer)

    @JvmStatic // JDK 9+
    public fun <T : Any> loadProviders(cls: KClass<T>, moduleLayer: ModuleLayer): Sequence<Provider<T>> =
        loadProviders(cls.java, moduleLayer)

    // Class overloads.
    // Note; they are different from JSL impl, which uses thread class loader by default

    @JvmStatic
    public fun <T : Any> load(cls: Class<T>): Sequence<T> = load(cls, cls.classLoader)

    @JvmStatic
    public fun <T : Any> loadProviders(cls: Class<T>): Sequence<Provider<T>> = loadProviders(cls, cls.classLoader)

    // Class and ClassLoader and ModuleLayer overloads

    @JvmStatic
    public fun <T : Any> load(cls: Class<T>, classLoader: ClassLoader): Sequence<T> = load(
        cls = cls,
        loadServices = { JavaServiceLoader.load(it, classLoader) },
        loadProviders = { JavaServiceLoader.load(it, classLoader) }
    )

    @JvmStatic
    public fun <T : Any> loadProviders(cls: Class<T>, classLoader: ClassLoader): Sequence<Provider<T>> = loadProviders(
        cls = cls,
        loadServices = { JavaServiceLoader.load(it, classLoader) },
        loadProviders = { JavaServiceLoader.load(it, classLoader) }
    )

    @JvmStatic // JDK 9+
    public fun <T : Any> load(cls: Class<T>, moduleLayer: ModuleLayer): Sequence<T> = load(
        cls = cls,
        loadServices = { JavaServiceLoader.load(moduleLayer, it) },
        loadProviders = { JavaServiceLoader.load(moduleLayer, it) }
    )

    @JvmStatic // JDK 9+
    public fun <T : Any> loadProviders(cls: Class<T>, moduleLayer: ModuleLayer): Sequence<Provider<T>> = loadProviders(
        cls = cls,
        loadServices = { JavaServiceLoader.load(moduleLayer, it) },
        loadProviders = { JavaServiceLoader.load(moduleLayer, it) }
    )

    public actual interface Provider<T : Any> : Lazy<T> {
        public actual val type: KClass<out T>
    }

    // JDK9+ (maybe Android)
    private val hasServiceLoaderProvider = try {
        Class.forName($$"java.util.ServiceLoader$Provider")
        true
    } catch (_: Throwable) {
        false
    }

    private inline fun <T : Any> load(
        cls: Class<T>,
        loadServices: (Class<T>) -> JavaServiceLoader<T>,
        loadProviders: (Class<out Provider<T>>) -> JavaServiceLoader<out Provider<T>>,
    ): Sequence<T> = when {
        shouldLoadDirectly(cls) -> loadServices(cls).asSequence()
        else                    -> loadProviders(getProviderClass(cls)).asSequence().map(Provider<T>::value)
    }.constrainOnce()

    private inline fun <T : Any> loadProviders(
        cls: Class<T>,
        loadServices: (Class<T>) -> JavaServiceLoader<T>,
        loadProviders: (Class<out Provider<T>>) -> JavaServiceLoader<out Provider<T>>,
    ): Sequence<Provider<T>> = when {
        shouldLoadDirectly(cls) -> when {
            hasServiceLoaderProvider -> loadServices(cls).stream().asSequence().map(::JavaProviderWrapper)
            else                     -> loadServices(cls).asSequence().map(::InstanceProviderWrapper)
        }
        else                    -> loadProviders(getProviderClass(cls)).asSequence()
    }.constrainOnce()

    // uses only JSL APIs
    private fun <T : Any> shouldLoadDirectly(cls: Class<T>): Boolean {
        return cls.getAnnotation(Service::class.java)?.compatibleWithJavaServiceLoader != false
    }

    // TODO: TBD generated interface name
    private fun <T : Any> getProviderClass(cls: Class<T>): Class<out Provider<T>> {
        val providerCls = cls.declaredClasses.firstOrNull { it.simpleName == $$"$Provider" } ?: error("Cannot find provider class for $cls")
        @Suppress("UNCHECKED_CAST")
        return providerCls as Class<out Provider<T>>
    }

    // JDK9+
    private class JavaProviderWrapper<T : Any>(private val provider: JavaServiceLoader.Provider<T>) : Provider<T> {
        override val type: KClass<out T> get() = provider.type().kotlin

        // additional lazy, as Java one doesn't support `isInitialized`
        private val delegate = lazy(provider::get)
        override val value: T get() = delegate.value
        override fun isInitialized(): Boolean = delegate.isInitialized()
    }

    private class InstanceProviderWrapper<T : Any>(override val value: T) : Provider<T> {
        override val type: KClass<out T> get() = value::class
        override fun isInitialized(): Boolean = true
    }
}

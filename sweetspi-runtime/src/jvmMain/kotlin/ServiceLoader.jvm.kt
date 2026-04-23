/*
 * Copyright (c) 2026 Oleg Yukhnevich. Use of this source code is governed by the Apache 2.0 license.
 */

package dev.whyoleg.sweetspi

import kotlin.reflect.*
import kotlin.streams.*
import java.util.ServiceLoader as JavaServiceLoader

// ModuleLayer and JSL provider are JDK9+

public actual object ServiceLoader {
    @JvmField
    @PublishedApi
    internal val hasServiceLoaderProvider: Boolean = try {
        Class.forName($$"java.util.ServiceLoader$Provider")
        true
    } catch (_: Throwable) {
        false
    }

    public actual inline fun <reified T : Any> load(): Sequence<T> = load(T::class)
    public actual inline fun <reified T : Any> loadProviders(): Sequence<Provider<T>> = loadProviders(T::class)

    @JvmStatic
    public actual fun <T : Any> load(cls: KClass<T>): Sequence<T> =
        load(cls.java, cls.java.classLoader)

    @JvmStatic
    public fun <T : Any> load(cls: KClass<T>, classLoader: ClassLoader): Sequence<T> =
        load(cls.java, classLoader)

    @JvmStatic
    public fun <T : Any> load(cls: KClass<T>, moduleLayer: ModuleLayer): Sequence<T> =
        load(cls.java, moduleLayer)

    @JvmStatic
    public fun <T : Any> load(cls: Class<T>): Sequence<T> =
        load(cls, cls.classLoader)

    @JvmStatic
    public fun <T : Any> load(cls: Class<T>, classLoader: ClassLoader): Sequence<T> = when {
        compatibleWithJavaServiceLoader(cls) -> {
            JavaServiceLoader.load(cls, classLoader).iterator().asSequence()
        }
        else                                 -> {
            JavaServiceLoader.load(getProviderClass(cls), classLoader).iterator().asSequence().map(Provider<T>::value)
        }
    }

    @JvmStatic
    public fun <T : Any> load(cls: Class<T>, moduleLayer: ModuleLayer): Sequence<T> = when {
        compatibleWithJavaServiceLoader(cls) -> {
            JavaServiceLoader.load(moduleLayer, cls).iterator().asSequence()
        }
        else                                 -> {
            JavaServiceLoader.load(moduleLayer, getProviderClass(cls)).iterator().asSequence().map(Provider<T>::value)
        }
    }

    @PublishedApi
    internal inline fun <reified T : Any> compilerLoadCompatible(): Sequence<T> {
        return JavaServiceLoader.load(T::class.java, T::class.java.classLoader).iterator().asSequence()
    }

    @PublishedApi
    internal inline fun <T : Any, reified P : Provider<T>> compilerLoad(): Sequence<T> {
        return JavaServiceLoader.load(P::class.java, P::class.java.classLoader).iterator().asSequence().map(Provider<T>::value)
    }

    @JvmStatic
    public actual fun <T : Any> loadProviders(cls: KClass<T>): Sequence<Provider<T>> =
        loadProviders(cls.java, cls.java.classLoader)

    @JvmStatic
    public fun <T : Any> loadProviders(cls: KClass<T>, classLoader: ClassLoader): Sequence<Provider<T>> =
        loadProviders(cls.java, classLoader)

    @JvmStatic
    public fun <T : Any> loadProviders(cls: KClass<T>, moduleLayer: ModuleLayer): Sequence<Provider<T>> =
        loadProviders(cls.java, moduleLayer)

    @JvmStatic
    public fun <T : Any> loadProviders(cls: Class<T>): Sequence<Provider<T>> =
        loadProviders(cls, cls.classLoader)

    @JvmStatic
    public fun <T : Any> loadProviders(cls: Class<T>, classLoader: ClassLoader): Sequence<Provider<T>> = when {
        compatibleWithJavaServiceLoader(cls) -> when {
            hasServiceLoaderProvider -> {
                JavaServiceLoader.load(cls, classLoader).stream().asSequence().constrainOnce().map(::provider)
            }
            else                     -> {
                JavaServiceLoader.load(cls, classLoader).iterator().asSequence().map(::provider)
            }
        }
        else                                 -> {
            JavaServiceLoader.load(getProviderClass(cls), classLoader).iterator().asSequence()
        }
    }

    @JvmStatic
    public fun <T : Any> loadProviders(cls: Class<T>, moduleLayer: ModuleLayer): Sequence<Provider<T>> = when {
        compatibleWithJavaServiceLoader(cls) -> when {
            hasServiceLoaderProvider -> {
                JavaServiceLoader.load(moduleLayer, cls).stream().asSequence().constrainOnce().map(::provider)
            }
            else                     -> {
                JavaServiceLoader.load(moduleLayer, cls).iterator().asSequence().map(::provider)
            }
        }
        else                                 -> {
            JavaServiceLoader.load(moduleLayer, getProviderClass(cls)).iterator().asSequence()
        }
    }

    @PublishedApi
    internal inline fun <reified T : Any> compilerLoadProvidersCompatible(): Sequence<Provider<T>> = when {
        hasServiceLoaderProvider -> {
            JavaServiceLoader.load(T::class.java, T::class.java.classLoader).stream().asSequence().constrainOnce().map(::provider)
        }
        else                     -> {
            JavaServiceLoader.load(T::class.java, T::class.java.classLoader).iterator().asSequence().map(::provider)
        }
    }

    @PublishedApi
    internal inline fun <T : Any, reified P : Provider<T>> compilerLoadProviders(): Sequence<Provider<T>> {
        return JavaServiceLoader.load(P::class.java, P::class.java.classLoader).iterator().asSequence()
    }

    public actual interface Provider<T : Any> : Lazy<T> {
        public actual val type: KClass<out T>
    }

    private fun <T : Any> compatibleWithJavaServiceLoader(cls: Class<T>): Boolean {
        return cls.getAnnotation(Service::class.java)?.compatibleWithJavaServiceLoader != false
    }

    private fun <T : Any> getProviderClass(cls: Class<T>): Class<out Provider<T>> {
        val providerCls = cls.declaredClasses.firstOrNull { it.simpleName == $$"$Provider" }
            ?: error("Cannot find provider class for $cls")
        @Suppress("UNCHECKED_CAST")
        return providerCls as Class<out Provider<T>>
    }

    @PublishedApi
    internal fun <T : Any> provider(provider: JavaServiceLoader.Provider<T>): Provider<T> = JavaProviderWrapper(provider)

    @PublishedApi
    internal fun <T : Any> provider(value: T): Provider<T> = InstanceProviderWrapper(value)

    private class JavaProviderWrapper<T : Any>(
        private val provider: JavaServiceLoader.Provider<T>,
    ) : Provider<T> {
        override val type: KClass<out T> get() = provider.type().kotlin
        private val delegate = lazy(provider::get)
        override val value: T get() = delegate.value
        override fun isInitialized(): Boolean = delegate.isInitialized()
    }

    private class InstanceProviderWrapper<T : Any>(
        override val value: T,
    ) : Provider<T> {
        override val type: KClass<out T> get() = value::class
        override fun isInitialized(): Boolean = true
    }
}

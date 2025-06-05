/*
 * Copyright (c) 2025 Oleg Yukhnevich. Use of this source code is governed by the Apache 2.0 license.
 */

@file:Suppress("Since15")

package dev.whyoleg.sweetspi

import kotlin.reflect.*
import java.util.ServiceLoader as JServiceLoader

public fun SomeService.Companion.serviceLoader(classLoader: ClassLoader): ServiceLoader3<SomeService> = TODO()
// public fun SomeService.Companion.serviceLoader(moduleLayer: ModuleLayer): ServiceLoader3<SomeService> = TODO()

public fun ServiceLoader.Default.classLoaderBased(classLoader: ClassLoader): ServiceLoader = ClassLoaderBasedServiceLoader(classLoader)
// TODO: support moduleBased later (needs JDK9+ to compile)
//public fun ServiceLoader.Default.moduleBased(moduleLayer: ModuleLayer): ServiceLoader = ModuleBasedServiceLoader(moduleLayer)

internal actual val DefaultServiceLoader: ServiceLoader get() = DefaultServiceLoaderImpl

private object DefaultServiceLoaderImpl : AbstractServiceLoader() {
    override fun <T : Any> loadServices(cls: Class<T>): JServiceLoader<T> = JServiceLoader.load(cls, cls.classLoader)
}

private class ClassLoaderBasedServiceLoader(private val classLoader: ClassLoader) : AbstractServiceLoader() {
    override fun <T : Any> loadServices(cls: Class<T>): JServiceLoader<T> = JServiceLoader.load(cls, classLoader)
}

//private class ModuleBasedServiceLoader(private val moduleLayer: ModuleLayer) : AbstractServiceLoader() {
//    override fun <T : Any> load(cls: Class<T>): JServiceLoader<T> = JServiceLoader.load(moduleLayer, cls)
//}

private abstract class AbstractServiceLoader : ServiceLoader {
    protected abstract fun <T : Any> loadServices(cls: Class<T>): JServiceLoader<T>

    // TODO: re-validate after JvmService/JvmServiceProvider support
    // TODO: re-validate regarding when to throw an error / when to call load (lazy or eager)
    final override fun <T : Any> loadServices(cls: KClass<T>): Sequence<T> {
        val serviceCls = cls.java
        if (!serviceCls.isAnnotationPresent(Service::class.java)) {
            error("${cls.simpleName} is not annotated with `@Service`")
        }
        return if (serviceCls.isAnnotationPresent(JvmService::class.java)) {
            Sequence { loadServices(serviceCls).iterator() }
        } else {
            // load provider class eagerly to ensure we can run service loader?
            val wrapperCls = wrapperCls(serviceCls)
            Sequence { loadServices(wrapperCls).iterator() }.map { it.invoke() }
        }
    }

    private fun <T : Any> wrapperCls(serviceCls: Class<T>): Class<() -> T> {
        val providerClassName = serviceCls.name + "\$Wrapper"
        val providerCls = requireNotNull(
            Class.forName(providerClassName, true, serviceCls.classLoader)
        ) {
            "Provider class not found: $providerClassName"
        }
        @Suppress("UNCHECKED_CAST")
        return providerCls as Class<() -> T>
    }
}

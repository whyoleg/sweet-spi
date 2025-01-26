/*
 * Copyright (c) 2025 Oleg Yukhnevich. Use of this source code is governed by the Apache 2.0 license.
 */

package dev.whyoleg.sweetspi

import kotlinx.atomicfu.locks.*
import kotlin.concurrent.*
import kotlin.reflect.*

@Suppress("DEPRECATION_ERROR")
internal actual val DefaultServiceLoader: ServiceLoader get() = InternalServiceRegistry

// TODO: validate concurrent operations
@Deprecated("used by compiler plugin only, shouldn't be used otherwise", level = DeprecationLevel.HIDDEN)
public object InternalServiceRegistry : ServiceRegistry, SynchronizedObject() {
    @Volatile
    private var initialized = false
    private val serviceProviders: MutableMap<KClass<*>, MutableList<() -> Any>> = hashMapOf()
    private val services: MutableSet<KClass<*>> = mutableSetOf()

    override fun <T : Any> registerService(cls: KClass<T>): Unit = synchronized(this) {
        check(!initialized) { "ServiceLoader was already initialized, no more services can be registered" }
        services += cls
    }

    override fun <T : Any> registerServiceProvider(cls: KClass<T>, provider: () -> T): Unit = synchronized(this) {
        check(!initialized) { "ServiceLoader was already initialized, no more services can be registered" }
        serviceProviders.getOrPut(cls, ::mutableListOf) += provider
    }

    override fun <T : Any> load(cls: KClass<T>): Sequence<T> {
        if (!initialized) synchronized(this) { initialized = true }

        require(cls in services) { "Service `${cls.simpleName}` wasn't registered" }

        @Suppress("UNCHECKED_CAST")
        val providers = serviceProviders[cls] as List<() -> T>? ?: return emptySequence()

        return providers.asSequence().map { it.invoke() }
    }
}

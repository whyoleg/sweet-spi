/*
 * Copyright (c) 2025 Oleg Yukhnevich. Use of this source code is governed by the Apache 2.0 license.
 */

package dev.whyoleg.sweetspi

import kotlin.reflect.*

// static vs dynamic loading?
// marker interface - may be something else in future?
public sealed interface PluginLoader<T : Any> : PluginSource<T>

// intrinsic?
public inline fun <reified P : Any> PluginLoader(): PluginLoader<P> = PluginLoader(P::class)
public fun <P : Any> PluginLoader(pluggable: KClass<P>): PluginLoader<P> = TODO()

// jvm only:
// withClassLoader(classLoader: ClassLoader): ServiceLoader2<T>
// withModuleLayer(moduleLayer: ModuleLayer): ServiceLoader2<T>
// JVM-only functions
public inline fun <reified P : Any> PluginLoader(classLoader: String): PluginLoader<P> = TODO()
public fun <P : Any> PluginLoader(pluggable: KClass<P>, classLoader: String): PluginLoader<P> = TODO()

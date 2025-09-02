/*
 * Copyright (c) 2025 Oleg Yukhnevich. Use of this source code is governed by the Apache 2.0 license.
 */

package dev.whyoleg.sweetspi

import kotlin.reflect.*

// lazy wrapper over pluggable implementation
public interface PluginReference<T : Any> : Lazy<T> {
    // Note: it's not always possible to get the type of the value without initializing it
    public val type: KClass<out T>
    public val annotations: List<Annotation> // PluginInfo annotated
}

//public fun <T : Any> PluginReference(value: T): PluginReference<T> = TODO()

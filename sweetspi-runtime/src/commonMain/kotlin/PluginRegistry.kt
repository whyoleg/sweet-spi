/*
 * Copyright (c) 2025 Oleg Yukhnevich. Use of this source code is governed by the Apache 2.0 license.
 */

package dev.whyoleg.sweetspi

import kotlin.reflect.*

public interface PluginRegistry<T : Any> : PluginSource<T> {
    // TODO: type can't be here, as it should be taken from the implementation, and so from the value itself
    public fun register(
        annotations: List<Annotation> = emptyList(),
        implementationClass: KClass<out T>? = null,
        provider: () -> T,
    )
}

// should be thread safe by default
public fun <T : Any> PluginRegistry(): PluginRegistry<T> = TODO()

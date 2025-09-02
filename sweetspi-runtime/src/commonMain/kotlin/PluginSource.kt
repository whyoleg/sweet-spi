/*
 * Copyright (c) 2025 Oleg Yukhnevich. Use of this source code is governed by the Apache 2.0 license.
 */

package dev.whyoleg.sweetspi

// in case `first` is needed -> sequence should be used
// PluginSource | PluginCatalog | PluginProvider | PluginContainer | PluginCollection(not a collection really)
public interface PluginSource<T : Any> {
    // will load new services on every call?
    // generic
    public fun get(): Sequence<PluginReference<T>>

    // most useful, can be faster? :)
    public fun getAll(): List<T> = get().mapTo(mutableListOf(), PluginReference<T>::value)
    public fun getSingle(): T = get().single().value
    public fun getSingleOrNull(): T? = get().singleOrNull()?.value
}

public fun <T : Any> PluginSource(elements: Iterable<T>): PluginSource<T> = TODO()
public fun <T : Any> PluginSource(sequence: Sequence<T>): PluginSource<T> = TODO()

//public fun <T : Any> PluginSource(block: suspend SequenceScope<T>.() -> Unit): PluginSource<T> = TODO()

//public suspend inline fun <T : Any> SequenceScope<T>.yieldFrom(source: PluginSource<T>): Unit = yieldAll(source.get())

public fun <T : Any> PluginSource<T>.withFallback(fallback: PluginSource<T>): PluginSource<T> = TODO()

// will load services on the first load and not reload services on `load`
public fun <T : Any> PluginSource<T>.cached(): PluginSource<T> = TODO()

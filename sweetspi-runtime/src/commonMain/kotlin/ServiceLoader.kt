/*
 * Copyright (c) 2024 Oleg Yukhnevich. Use of this source code is governed by the Apache 2.0 license.
 */

@file:Suppress("PackageDirectoryMismatch")

package dev.whyoleg.sweetspi

import dev.whyoleg.sweetspi.internal.*
import kotlin.jvm.*
import kotlin.reflect.*

@Target(
    AnnotationTarget.CLASS // only interface or abstract class
)
public annotation class Service

// `services` are used to identify for which service to provide instance
// if no services provided - plugin will try to find all supertypes which has `Service` annotation
// KSP2/AA doesn't support annotation arguments for targets other than JVM
@Target(
    AnnotationTarget.CLASS,    // only object
    AnnotationTarget.FUNCTION, // no arguments, no receiver, non-suspend, no generic
    AnnotationTarget.PROPERTY, // no receiver, non-suspend getter, no generic
)
public annotation class ServiceProvider(
    public vararg val services: KClass<*>,
)

public object ServiceLoader {
    @JvmStatic
    @OptIn(InternalSweetSpiApi::class)
    public fun <T : Any> load(cls: KClass<T>): List<T> = internalServiceLoader.value.load(cls)
}

public inline fun <reified T : Any> ServiceLoader.load(): List<T> = load(T::class)

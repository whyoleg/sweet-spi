/*
 * Copyright (c) 2025 Oleg Yukhnevich. Use of this source code is governed by the Apache 2.0 license.
 */

package dev.whyoleg.sweetspi

import kotlin.reflect.*

// TBD name - for compatibility with java.util.ServiceLoader - will not generate wrapper (some features might not work)
//  it will be not possible to have objects or top-level functions as ServiceProvider - only open/final class
//  it may work in case of interface + implementation by delegation
@MustBeDocumented
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.BINARY)
public annotation class JvmService // JavaServiceLoaderCompatibleService :)

// needed in case `Service` is not annotated - replacement for auto-service
@MustBeDocumented
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.BINARY)
public annotation class JvmProvides(public val service: KClass<*>) // `service` can be anything

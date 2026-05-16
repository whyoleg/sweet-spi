/*
 * Copyright (c) 2025-2026 Oleg Yukhnevich. Use of this source code is governed by the Apache 2.0 license.
 */

package dev.whyoleg.sweetspi.compiler

import org.jetbrains.kotlin.name.*

object SweetClassIds {
    private val pkg = FqName("dev.whyoleg.sweetspi")
    private fun classId(name: String): ClassId = ClassId(pkg, Name.identifier(name))
    val Service = classId("Service")
    val Provides = classId("Provides")
    val ServiceLoader = classId("ServiceLoader")
    val ServiceProvider = classId("ServiceProvider")
    val ServiceProviderRegistry = classId("ServiceProviderRegistry")
    val ServiceProviderRegistryHolder = classId("ServiceProviderRegistryHolder")
}

// nested classes
object SweetNames {
    val SERVICE_PROVIDER_NAME = Name.identifier($$"$ServiceProvider")
    val SERVICE_PROVIDER_REGISTRY_NAME = Name.identifier($$"$ServiceProviderRegistry")
}

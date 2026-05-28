/*
 * Copyright (c) 2025-2026 Oleg Yukhnevich. Use of this source code is governed by the Apache 2.0 license.
 */

package dev.whyoleg.sweetspi.compiler

import org.jetbrains.kotlin.name.*

object SweetNames {
    private val pkg = FqName("dev.whyoleg.sweetspi")
    private fun classId(name: String): ClassId = ClassId(pkg, Name.identifier(name))
    val Service = classId("Service")
    val Provides = classId("Provides")

    val ServiceProvider = classId("ServiceProvider")
    val ServiceProviderRegistry = classId("ServiceProviderRegistry")
    val ServiceProviderRegistryHolder = classId("ServiceProviderRegistryHolder")

    // generated nested classes
    val ServiceProviderName = Name.identifier($$"$ServiceProvider")
    val ServiceProviderRegistryName = Name.identifier($$"$ServiceProviderRegistry")
    fun serviceProviderImplementationName(serviceClassId: ClassId): Name = Name.identifier(
        $$"$ServiceProvider$$${serviceClassId.sanitize()}"
    )

    // top-level property
    fun serviceProviderInitializerName(
        serviceClassId: ClassId,
        serviceImplClassId: ClassId,
    ): Name = Name.identifier(
        $$"$ServiceProviderInitializer$$${serviceClassId.sanitize()}$$${serviceImplClassId.sanitize()}"
    )

    private val ServiceLoader = classId("ServiceLoader")
    private val ServiceLoaderLoad = CallableId(ServiceLoader, Name.identifier("load"))
    private val JavaServiceLoader = ClassId(FqName("java.util"), Name.identifier("ServiceLoader"))
    private val JavaServiceLoaderLoad = CallableId(JavaServiceLoader, Name.identifier("load"))

    val AllServiceLoaderCalls = setOf(
        ServiceLoaderLoad,
        JavaServiceLoaderLoad
    )

    private fun ClassId.sanitize(): String = asFqNameString().replace('.', '_')
}

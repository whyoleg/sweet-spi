/*
 * Copyright (c) 2025 Oleg Yukhnevich. Use of this source code is governed by the Apache 2.0 license.
 */

package dev.whyoleg.sweetspi.compiler.common

import org.jetbrains.kotlin.*
import org.jetbrains.kotlin.name.*

object SweetClassIds {
    private val pkg = FqName("dev.whyoleg.sweetspi")
    val Service = ClassId(pkg, Name.identifier("Service"))
    val ServiceProvider = ClassId(pkg, Name.identifier("ServiceProvider"))
    val InternalServiceRegistry = ClassId(pkg, Name.identifier("InternalServiceProvider"))

    fun wrapper(original: ClassId): ClassId = ClassId(
        original.packageFqName,
        Name.identifier("${original.shortClassName.identifier}\$Wrapper")
    )
}

object SweetServiceProviderParameterNames {
    val services = Name.identifier("services")
}

data object SweetDeclarationKey : GeneratedDeclarationKey()

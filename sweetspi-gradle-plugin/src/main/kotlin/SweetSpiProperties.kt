/*
 * Copyright (c) 2024-2025 Oleg Yukhnevich. Use of this source code is governed by the Apache 2.0 license.
 */

package dev.whyoleg.sweetspi.gradle

import java.util.*

internal object SweetSpiProperties : Properties() {
    private fun readResolve(): Any = SweetSpiProperties

    init {
        SweetSpiProperties::class.java.classLoader.getResourceAsStream("sweetspi.properties").use(::load)
    }

    val version: String by property("version")

    private fun property(name: String): Lazy<String> = lazy {
        getProperty("sweetspi.$name") ?: error("Failed to load `sweetspi.$name` from properties")
    }
}

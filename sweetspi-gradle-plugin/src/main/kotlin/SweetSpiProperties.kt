/*
 * Copyright (c) 2024 Oleg Yukhnevich. Use of this source code is governed by the Apache 2.0 license.
 */

package dev.whyoleg.sweetspi.gradle

import java.io.*
import java.util.*

internal object SweetSpiProperties {
    private val properties = loadProperties()

    val version: String by property("version")

    private fun property(name: String): Lazy<String> = lazy {
        properties.getProperty("sweetspi.$name") ?: error("Failed to load `sweetspi.$name` from properties")
    }

    private fun loadProperties(): Properties {
        val properties = Properties()
        SweetSpiProperties::class.java.classLoader
            ?.getResourceAsStream("sweetspi.properties")
            ?.use(properties::load)
            ?: throw FileNotFoundException("Failed to load `sweetspi.properties` from classpath")

        return properties
    }
}

/*
 * Copyright (c) 2024-2025 Oleg Yukhnevich. Use of this source code is governed by the Apache 2.0 license.
 */

package dev.whyoleg.sweetspi.tests

object TestsArguments {
    val devArtifactsDirectories = systemProperty("dev-artifacts-directories").split(",")
    val devArtifactsVersion = systemProperty("dev-artifacts-version")

    private fun systemProperty(name: String): String = checkNotNull(System.getProperty("sweettests.$name")) {
        "'sweettests.$name' is missing in the system properties"
    }
}

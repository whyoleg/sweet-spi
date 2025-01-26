/*
 * Copyright (c) 2024-2025 Oleg Yukhnevich. Use of this source code is governed by the Apache 2.0 license.
 */

pluginManagement {
    includeBuild("build-logic")
    includeBuild("build-settings")
}

plugins {
    id("sweetsettings.default")
}

rootProject.name = "sweet-spi"

includeBuild("sweetspi-runtime")
includeBuild("sweetspi-compiler-plugin")
includeBuild("sweetspi-gradle-plugin")
includeBuild("sweetspi-tests")

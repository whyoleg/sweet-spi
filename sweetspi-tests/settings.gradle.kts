/*
 * Copyright (c) 2024-2025 Oleg Yukhnevich. Use of this source code is governed by the Apache 2.0 license.
 */

pluginManagement {
    includeBuild("../build-logic")
    includeBuild("../build-settings")

    includeBuild("../sweetspi-gradle-plugin")
}

includeBuild("../sweetspi-runtime")
includeBuild("../sweetspi-compiler-plugin")

plugins {
    id("sweetsettings.default")
}

dependencyResolutionManagement {
    versionCatalogs.named("libs") {
        from(files("../gradle/libs.versions.toml"))
    }
}

rootProject.name = "sweetspi-tests"

include("jvm")
include("multiplatform")
include("multiplatform-multimodule")

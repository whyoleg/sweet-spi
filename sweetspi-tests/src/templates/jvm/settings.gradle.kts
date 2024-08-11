/*
 * Copyright (c) 2024 Oleg Yukhnevich. Use of this source code is governed by the Apache 2.0 license.
 */

pluginManagement {
    repositories {
        %DEV_ARTIFACTS_REPOSITORIES
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositories {
        %DEV_ARTIFACTS_REPOSITORIES
        mavenCentral()
    }
}

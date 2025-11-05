/*
 * Copyright (c) 2024 Oleg Yukhnevich. Use of this source code is governed by the Apache 2.0 license.
 */

plugins {
    id("sweetbuild.kotlin")
    alias(libs.plugins.kotlin.multiplatform)
}

kotlin {
    explicitApi()

    jvm()
    js {
        nodejs()
        browser()
    }
    wasmJs {
        nodejs()
        browser()
    }

    // desktop native targets
    macosX64()
    macosArm64 {
        binaries.sharedLib {
            baseName = "hello"
        }
    }

    linuxX64()
    linuxArm64()

    mingwX64()

    sourceSets {
        commonMain.dependencies {
            implementation(projects.kxcliApi)
        }
    }
}

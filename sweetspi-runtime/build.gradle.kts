/*
 * Copyright (c) 2024-2025 Oleg Yukhnevich. Use of this source code is governed by the Apache 2.0 license.
 */

import com.vanniktech.maven.publish.*
import kotlinx.validation.*
import org.jetbrains.dokka.gradle.*
import org.jetbrains.kotlin.gradle.*
import org.jetbrains.kotlin.gradle.targets.js.dsl.*
import org.jetbrains.kotlin.gradle.targets.js.nodejs.*
import org.jetbrains.kotlin.gradle.targets.js.npm.*

plugins {
    id("sweetbuild.kotlin")
    id("sweetbuild.publication")
    id("sweetbuild.documentation")
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.kotlinx.bcv)
    alias(libs.plugins.kotlinx.atomicfu)
}

description = "sweet-spi runtime API"

mavenPublishing {
    configure(KotlinMultiplatform())
}

apiValidation {
    @OptIn(ExperimentalBCVApi::class)
    klib.enabled = true
}

@OptIn(ExperimentalKotlinGradlePluginApi::class, ExperimentalWasmDsl::class)
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
    wasmWasi {
        nodejs()
    }

    iosArm64()
    iosX64()
    iosSimulatorArm64()

    watchosX64()
    watchosArm32()
    watchosArm64()
    watchosSimulatorArm64()
    watchosDeviceArm64()

    tvosX64()
    tvosArm64()
    tvosSimulatorArm64()

    macosX64()
    macosArm64()

    linuxX64()
    linuxArm64()

    mingwX64()

    androidNativeX64()
    androidNativeX86()
    androidNativeArm64()
    androidNativeArm32()

    applyDefaultHierarchyTemplate {
        common {
            group("nonJvm") {
                withJs()
                withWasmJs()
                withWasmWasi()
                group("native")
            }
        }
    }
}

plugins.withType<NodeJsRootPlugin> {
    // ignore package lock
    extensions.configure<NpmExtension> {
        lockFileDirectory.set(layout.buildDirectory.dir("kotlin-js-store"))
        packageLockMismatchReport.set(LockFileMismatchReport.NONE)
    }
}

tasks.withType<DokkaTask>().configureEach {
    outputDirectory.set(rootDir.resolve("../docs/runtime-api"))
}

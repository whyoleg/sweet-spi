/*
 * Copyright (c) 2024-2025 Oleg Yukhnevich. Use of this source code is governed by the Apache 2.0 license.
 */

@file:OptIn(ExperimentalKotlinGradlePluginApi::class, ExperimentalWasmDsl::class, ExperimentalAbiValidation::class)

import com.vanniktech.maven.publish.*
import org.jetbrains.dokka.gradle.*
import org.jetbrains.kotlin.gradle.*
import org.jetbrains.kotlin.gradle.dsl.abi.*

plugins {
    id("sweetbuild.kotlin")
    id("sweetbuild.publication")
    id("sweetbuild.documentation")
    alias(libs.plugins.kotlin.multiplatform)
}

description = "sweet-spi runtime API"

mavenPublishing {
    configure(KotlinMultiplatform())
}

kotlin {
    explicitApi()
    abiValidation { enabled = true }

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

    @Suppress("DEPRECATION") watchosX64()
    watchosArm32()
    watchosArm64()
    watchosSimulatorArm64()
    watchosDeviceArm64()

    @Suppress("DEPRECATION") tvosX64()
    tvosArm64()
    tvosSimulatorArm64()

    @Suppress("DEPRECATION") macosX64()
    macosArm64()

    linuxX64()
    linuxArm64()

    mingwX64()

    androidNativeX64()
    androidNativeX86()
    androidNativeArm64()
    androidNativeArm32()

    compilerOptions {
        freeCompilerArgs.add("-Xexpect-actual-classes")
    }

    applyDefaultHierarchyTemplate {
        common {
            group("nonJvm") {
                group("web")
                group("native")
                withWasmWasi()
            }
        }
    }
}

tasks.withType<DokkaTask>().configureEach {
    outputDirectory.set(rootDir.resolve("docs/runtime-api"))

    dokkaSourceSets.configureEach {
        perPackageOption {
            suppress = true
            matchingRegex = """.*\.internal.*"""
        }
    }
}

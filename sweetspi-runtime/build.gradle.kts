/*
 * Copyright (c) 2024 Oleg Yukhnevich. Use of this source code is governed by the Apache 2.0 license.
 */

import com.vanniktech.maven.publish.*
import kotlinx.validation.*
import org.jetbrains.kotlin.gradle.*
import org.jetbrains.kotlin.gradle.targets.js.dsl.*

plugins {
    id("sweetbuild.kotlin")
    id("sweetbuild.publication")
    id("sweetbuild.documentation")
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.kotlinx.bcv)
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

    compilerOptions {
        freeCompilerArgs.add("-Xexpect-actual-classes")
    }

    applyDefaultHierarchyTemplate {
        common {
            group("nonJvm") {
                group("jsAndWasmShared") {
                    withJs()
                    withWasmJs()
                    withWasmWasi()
                }
                // mutex native API isn't commonized properly, so we need to define additional source sets to overcome it
                group("native") {
                    group("nix") {
                        group("linux")
                        group("apple")
                    }
                }
            }
        }
    }

    // version enforcement using bom works only for jvm
    sourceSets.jvmMain.dependencies {
        api(project.dependencies.platform(projects.sweetspiBom))
    }
}

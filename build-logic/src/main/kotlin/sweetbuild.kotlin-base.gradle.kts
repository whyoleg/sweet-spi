/*
 * Copyright (c) 2024 Oleg Yukhnevich. Use of this source code is governed by the Apache 2.0 license.
 */

import org.jetbrains.kotlin.gradle.*
import org.jetbrains.kotlin.gradle.dsl.*
import org.jetbrains.kotlin.gradle.plugin.*
import org.jetbrains.kotlin.gradle.targets.jvm.*

plugins {
    id("org.jetbrains.kotlinx.kover")
}

@OptIn(ExperimentalKotlinGradlePluginApi::class)
plugins.withType<KotlinBasePluginWrapper>().configureEach {
    extensions.configure<KotlinProjectExtension>("kotlin") {

        // true by default
        val warningsAsErrors = providers.gradleProperty("sweetbuild.warningsAsErrors").orNull?.toBoolean() ?: true

        if (providers.gradleProperty("sweetbuild.skipTests").map(String::toBoolean).getOrElse(false)) {
            tasks.matching { it is AbstractTestTask }.configureEach { onlyIf { false } }
        }

        fun KotlinCommonCompilerOptions.configureCommonOptions() {
            allWarningsAsErrors.set(warningsAsErrors)
            progressiveMode.set(true)
            freeCompilerArgs.add("-Xrender-internal-diagnostic-names")
        }

        fun KotlinJvmCompilerOptions.configureJvmOptions() {
            freeCompilerArgs.add("-Xjvm-default=all")
        }

        when (this) {
            is KotlinJvmProjectExtension    -> {
                compilerOptions.configureCommonOptions()
                target {
                    compilerOptions.configureJvmOptions()
                }
            }
            is KotlinMultiplatformExtension -> {
                compilerOptions.configureCommonOptions()
                targets.withType<KotlinJvmTarget>().configureEach {
                    compilerOptions.configureJvmOptions()
                }
            }
        }
    }
}

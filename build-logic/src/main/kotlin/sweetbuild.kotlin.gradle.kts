/*
 * Copyright (c) 2024 Oleg Yukhnevich. Use of this source code is governed by the Apache 2.0 license.
 */

import org.gradle.api.tasks.testing.*
import org.jetbrains.kotlin.gradle.*
import org.jetbrains.kotlin.gradle.dsl.*
import org.jetbrains.kotlin.gradle.plugin.*
import org.jetbrains.kotlin.gradle.targets.jvm.*
import sweetbuild.*

val warningsAsErrors = booleanProperty("sweetbuild.warningsAsErrors", defaultValue = true)
val skipTestTasks = booleanProperty("sweetbuild.skipTests", defaultValue = false)

@OptIn(ExperimentalKotlinGradlePluginApi::class)
plugins.withType<KotlinBasePluginWrapper>().configureEach {
    extensions.configure<KotlinProjectExtension>("kotlin") {
        fun KotlinCommonCompilerOptions.configureCommonOptions() {
            allWarningsAsErrors.set(warningsAsErrors)
            progressiveMode.set(true)
            freeCompilerArgs.add("-Xrender-internal-diagnostic-names")
        }

        fun KotlinJvmCompilerOptions.configureJvmOptions() {
            jvmDefault.set(JvmDefaultMode.NO_COMPATIBILITY)
            jvmTarget.set(JvmTarget.JVM_1_8)
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

plugins.withType<JavaBasePlugin>().configureEach {
    extensions.configure<JavaPluginExtension>("java") {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
}

tasks.matching { it is AbstractTestTask }.configureEach {
    val skipTestTasks = skipTestTasks // for CC
    onlyIf { !skipTestTasks.get() }
}

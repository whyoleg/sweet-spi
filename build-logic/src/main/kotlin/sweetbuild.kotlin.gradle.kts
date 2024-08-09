/*
 * Copyright (c) 2024 Oleg Yukhnevich. Use of this source code is governed by the Apache 2.0 license.
 */

import org.jetbrains.kotlin.gradle.dsl.*
import org.jetbrains.kotlin.gradle.plugin.*
import org.jetbrains.kotlin.gradle.targets.jvm.*

plugins {
    id("sweetbuild.kotlin-base")
}

@Suppress("UnstableApiUsage")
plugins.withType<KotlinBasePluginWrapper>().configureEach {
    extensions.configure<KotlinProjectExtension>("kotlin") {
        val javaToolchains = extensions.getByName<JavaToolchainService>("javaToolchains")

        val jdkToolchainVersion = 8
        val jdkTestVersions = setOf(11, 17, 21)

        fun TaskProvider<out Test>.jvmToolchain(jdkVersion: Int) {
            configure {
                javaLauncher.set(javaToolchains.launcherFor {
                    languageVersion.set(JavaLanguageVersion.of(jdkVersion))
                })
            }
        }

        when (this) {
            is KotlinJvmProjectExtension    -> {
                jvmToolchain(jdkToolchainVersion)
                plugins.apply("org.gradle.jvm-test-suite")
                extensions.configure<TestingExtension>("testing") {
                    val test by suites.getting(JvmTestSuite::class) {
                        jdkTestVersions.forEach { jdkTestVersion ->
                            targets.register("test${jdkTestVersion}") {
                                testTask.jvmToolchain(jdkTestVersion)
                            }
                        }
                    }
                }
            }
            is KotlinMultiplatformExtension -> {
                jvmToolchain(jdkToolchainVersion)
                targets.withType<KotlinJvmTarget>().configureEach {
                    jdkTestVersions.forEach { jdkTestVersion ->
                        testRuns.create("${jdkTestVersion}Test") {
                            executionTask.jvmToolchain(jdkTestVersion)
                        }
                    }
                }
            }
        }
    }
}

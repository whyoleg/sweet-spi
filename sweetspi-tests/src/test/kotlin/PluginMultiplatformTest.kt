/*
 * Copyright (c) 2024 Oleg Yukhnevich. Use of this source code is governed by the Apache 2.0 license.
 */

package dev.whyoleg.sweetspi.tests

import org.gradle.testkit.runner.*
import org.junit.jupiter.params.*
import org.junit.jupiter.params.provider.*

class PluginMultiplatformTest : AbstractTest() {
    override val defaultTemplate: TestTemplate get() = TestTemplate.MULTIPLATFORM

    @ParameterizedTest
    @ArgumentsSource(TestVersionsProvider.All::class)
    fun testJvm(versions: TestVersions) {
        val project = project(versions = versions) {
            append("build.gradle.kts") {
                """
                kotlin.withSweetSpi()
                kotlin.jvm()
                """.trimIndent()
            }
            file("src/jvmMain/kotlin/main.kt") {
                // language=kotlin
                """
                package sweettests.multiplatform

                import dev.whyoleg.sweetspi.*

                @Service interface SimpleService
                @ServiceProvider object SimpleServiceImpl : SimpleService
                """.trimIndent()
            }
        }
        project.gradleRunner("build").build().apply {
            assert(task(":kspKotlinJvm")!!.outcome in setOf(TaskOutcome.SUCCESS, TaskOutcome.FROM_CACHE))
        }
    }

    @ParameterizedTest
    @ArgumentsSource(TestVersionsProvider.All::class)
    fun testAllTargets(versions: TestVersions) {
        val project = project(versions = versions) {
            append("build.gradle.kts") {
                """
                kotlin {
                  withSweetSpi()
                  
                  jvm()
                  js { nodejs(); browser() }
                  wasmJs { nodejs(); browser() }
                  wasmWasi { nodejs() }             
                  iosArm64(); iosX64(); iosSimulatorArm64()              
                  watchosX64(); watchosArm32(); watchosArm64(); watchosSimulatorArm64(); watchosDeviceArm64()              
                  tvosX64(); tvosArm64(); tvosSimulatorArm64()              
                  macosX64(); macosArm64()                  
                  linuxX64(); linuxArm64()              
                  mingwX64()              
                  androidNativeX64(); androidNativeX86(); androidNativeArm64(); androidNativeArm32()
                }
                """.trimIndent()
            }
            file("src/commonMain/kotlin/main.kt") {
                // language=kotlin
                """
                package sweettests.multiplatform

                import dev.whyoleg.sweetspi.*

                @Service interface SimpleService
                @ServiceProvider object SimpleServiceImpl : SimpleService
                """.trimIndent()
            }
        }
        project.gradleRunner("build").build().apply {
            assert(task(":kspKotlinJvm")!!.outcome in setOf(TaskOutcome.SUCCESS, TaskOutcome.FROM_CACHE))
            assert(task(":kspKotlinJs")!!.outcome in setOf(TaskOutcome.SUCCESS, TaskOutcome.FROM_CACHE))
            assert(task(":kspKotlinWasmJs")!!.outcome in setOf(TaskOutcome.SUCCESS, TaskOutcome.FROM_CACHE))
            assert(task(":kspKotlinWasmWasi")!!.outcome in setOf(TaskOutcome.SUCCESS, TaskOutcome.FROM_CACHE))

            // tasks are different on different OS
            assert(
                tasks.filter {
                    it.path.startsWith(":kspKotlinMacos") ||
                            it.path.startsWith(":kspKotlinLinux") ||
                            it.path.startsWith(":kspKotlinMingw")
                }.any { it.outcome in setOf(TaskOutcome.SUCCESS, TaskOutcome.FROM_CACHE) }
            )
        }
    }
}

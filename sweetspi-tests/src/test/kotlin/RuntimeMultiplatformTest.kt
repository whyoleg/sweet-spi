/*
 * Copyright (c) 2024 Oleg Yukhnevich. Use of this source code is governed by the Apache 2.0 license.
 */

package dev.whyoleg.sweetspi.tests

import org.gradle.testkit.runner.*
import kotlin.test.*

class RuntimeMultiplatformTest : AbstractTest() {
    override val defaultTemplate: TestTemplate get() = TestTemplate.MULTIPLATFORM
    override val defaultVersions: TestVersions get() = TestsArguments.defaultTestVersions

    @Test
    fun testJvm() {
        val project = project {
            append("build.gradle.kts") {
                """
                kotlin.withSweetSpi()
                kotlin.jvm()
                kotlin { sourceSets.jvmTest.dependencies { implementation(kotlin("test")) } }
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
            file("src/jvmTest/kotlin/test.kt") {
                // language=kotlin
                """
                package sweettests.multiplatform

                import dev.whyoleg.sweetspi.*
                import kotlin.test.*
                
                class SimpleTest {
                    @Test
                    fun doTest() {
                        val services = ServiceLoader.load<SimpleService>()
                        assertEquals(1, services.size)
                        val service = services.single()
                        assertEquals(SimpleServiceImpl, service)
                    }
                }
                """.trimIndent()
            }
        }
        project.gradleRunner("build").build().apply {
            assert(task(":jvmTest")!!.outcome in setOf(TaskOutcome.SUCCESS, TaskOutcome.FROM_CACHE))
        }
    }

    @Test
    fun testAllTargets() {
        val project = project {
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
                  
                  sourceSets.commonTest.dependencies { implementation(kotlin("test")) }
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
            file("src/commonTest/kotlin/test.kt") {
                // language=kotlin
                """
                package sweettests.multiplatform

                import dev.whyoleg.sweetspi.*
                import kotlin.test.*
                
                class SimpleTest {
                    @Test
                    fun doTest() {
                        val services = ServiceLoader.load<SimpleService>()
                        assertEquals(1, services.size)
                        val service = services.single()
                        assertEquals(SimpleServiceImpl, service)
                    }
                }
                """.trimIndent()
            }
        }
        project.gradleRunner("build").build().apply {
            assert(task(":jvmTest")!!.outcome in setOf(TaskOutcome.SUCCESS, TaskOutcome.FROM_CACHE))
            assert(task(":jsTest")!!.outcome in setOf(TaskOutcome.SUCCESS, TaskOutcome.FROM_CACHE))
            assert(task(":wasmJsTest")!!.outcome in setOf(TaskOutcome.SUCCESS, TaskOutcome.FROM_CACHE))
            assert(task(":wasmWasiTest")!!.outcome in setOf(TaskOutcome.SUCCESS, TaskOutcome.FROM_CACHE))

            // different tasks are on different OS
            assert(
                tasks.filter {
                    it.path.startsWith(":macos") || it.path.startsWith(":linux") || it.path.startsWith(":mingw")
                }.any { it.outcome in setOf(TaskOutcome.SUCCESS, TaskOutcome.FROM_CACHE) }
            )
        }
    }
}

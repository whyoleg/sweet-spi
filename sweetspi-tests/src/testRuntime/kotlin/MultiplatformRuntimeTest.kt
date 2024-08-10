/*
 * Copyright (c) 2024 Oleg Yukhnevich. Use of this source code is governed by the Apache 2.0 license.
 */

package dev.whyoleg.sweetspi.tests.runtime

import dev.whyoleg.sweetspi.tests.*
import kotlin.test.*

class MultiplatformRuntimeTest : AbstractTest() {
    override val defaultTemplate: TestTemplate get() = TestTemplate.MULTIPLATFORM
    override val defaultVersions: TestVersions get() = TestsArguments.defaultTestVersions

    @Test
    fun testJvm() {
        val project = project {
            withSweetSpi()
            jvmTarget()
            kotlinTest(JVM_TEST)
            kotlinSourceFile(
                sourceSet = JVM_MAIN,
                path = "main.kt",
                code = """
                @Service interface SimpleService
                @ServiceProvider object SimpleServiceImpl : SimpleService
                """.trimIndent()
            )
            kotlinSourceFile(
                sourceSet = JVM_TEST,
                path = "test.kt",
                code = """
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
            )
        }
        project.gradle("build") {
            assert(task(":jvmTest")!!.outcome.isPositive)
        }
    }

    @Test
    fun testAllTargets() {
        val project = project {
            prepend("build.gradle.kts") {
                // for native tasks
                "import org.jetbrains.kotlin.gradle.plugin.mpp.*"
            }
            withSweetSpi()
            allTargets()
            kotlinTest(COMMON_TEST)
            append("build.gradle.kts") {
                """
                kotlin {
                  // setup tests running in RELEASE mode
                  targets.withType<KotlinNativeTarget>().configureEach {
                      binaries.test(listOf(NativeBuildType.RELEASE))
                  }
                  targets.withType<KotlinNativeTargetWithTests<*>>().configureEach {
                      testRuns.create("releaseTest") {
                          setExecutionSourceFrom(binaries.getTest(NativeBuildType.RELEASE))
                      }
                  }
                }
                """.trimIndent()
            }
            kotlinSourceFile(
                sourceSet = COMMON_MAIN, path = "main.kt",
                code = """
                @Service interface SimpleService
                @ServiceProvider object SimpleServiceImpl : SimpleService
                """.trimIndent()
            )
            kotlinSourceFile(
                sourceSet = COMMON_TEST, path = "test.kt",
                code = """
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
            )
        }
        project.gradle("build") {
            assert(task(":jvmTest")!!.outcome.isPositive)
            assert(task(":jsTest")!!.outcome.isPositive)
            assert(task(":wasmJsTest")!!.outcome.isPositive)
            assert(task(":wasmWasiTest")!!.outcome.isPositive)

            // tasks are different on different OS, only desktop targets are mentioned
            val nativeTestTasks = setOf(
                ":macosArm64Test",
                ":macosX64Test",
                ":linuxX64Test",
                ":linuxArm64Test",
                ":mingwX64Test",
            )
            assert(tasks.any { it.path in nativeTestTasks && it.outcome.isPositive })
        }
    }
}

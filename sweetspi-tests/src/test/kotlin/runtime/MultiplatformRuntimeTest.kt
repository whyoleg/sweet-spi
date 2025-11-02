/*
 * Copyright (c) 2024-2025 Oleg Yukhnevich. Use of this source code is governed by the Apache 2.0 license.
 */

package dev.whyoleg.sweetspi.tests.runtime

import dev.whyoleg.sweetspi.tests.*

class MultiplatformRuntimeTest : AbstractTest() {
    override val defaultTemplate: TestTemplate get() = TestTemplate.MULTIPLATFORM

    @FastVersionedTest
    fun testAllTargets(versions: TestVersions) {
        val project = project(versions) {

            withSweetSpi()
            allTargets()
            kotlinTest(COMMON_TEST)
            prepend(BUILD_GRADLE_KTS) {
                // for native tasks
                "import org.jetbrains.kotlin.gradle.plugin.mpp.*"
            }
            append(BUILD_GRADLE_KTS) {
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

    @FastVersionedTest
    fun testAllTargetsExplicitServiceProvider(versions: TestVersions) {
        val project = project(versions) {

            withSweetSpi()
            allTargets()
            kotlinTest(COMMON_TEST)
            prepend(BUILD_GRADLE_KTS) {
                // for native tasks
                "import org.jetbrains.kotlin.gradle.plugin.mpp.*"
            }
            append(BUILD_GRADLE_KTS) {
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
                @Service interface SimpleService1
                @Service interface SimpleService2
                @ServiceProvider(SimpleService1::class) object SimpleServiceImpl : SimpleService1, SimpleService2
                """.trimIndent()
            )
            kotlinSourceFile(
                sourceSet = COMMON_TEST, path = "test.kt",
                code = """
                import kotlin.test.*
                
                class SimpleTest {
                    @Test
                    fun doTestService1() {
                        val services = ServiceLoader.load<SimpleService1>()
                        assertEquals(1, services.size)
                        val service = services.single()
                        assertEquals(SimpleServiceImpl, service)
                    }

                    @Test
                    fun doTestService2() {
                        val services = ServiceLoader.load<SimpleService2>()
                        assertEquals(0, services.size)
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

    @FastVersionedTest
    fun testAllTargetsClassServices(versions: TestVersions) {
        val project = project(versions) {

            withSweetSpi()
            allTargets()
            kotlinTest(COMMON_TEST)
            prepend(BUILD_GRADLE_KTS) {
                // for native tasks
                "import org.jetbrains.kotlin.gradle.plugin.mpp.*"
            }
            append(BUILD_GRADLE_KTS) {
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
                @ServiceProvider class SimpleServiceImpl2 : SimpleService
                @ServiceProvider class SimpleServiceImpl3() : SimpleService
                @ServiceProvider class SimpleServiceImpl4(val a: Int = 4) : SimpleService
                @ServiceProvider class SimpleServiceImpl5(val a: Int) : SimpleService {
                    constructor() : this(5)
                }
                @ServiceProvider class SimpleServiceImpl6(val a: Int) : SimpleService {
                    constructor(b: String = "5") : this(6)
                }
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
                        assertEquals(6, services.size)
                        assertEquals(
                            setOf(
                                SimpleServiceImpl::class,
                                SimpleServiceImpl2::class,
                                SimpleServiceImpl3::class,
                                SimpleServiceImpl4::class,
                                SimpleServiceImpl5::class,
                                SimpleServiceImpl6::class,
                            ), 
                            services.map { it::class }.toSet()
                        )
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

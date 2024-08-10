/*
 * Copyright (c) 2024 Oleg Yukhnevich. Use of this source code is governed by the Apache 2.0 license.
 */

package dev.whyoleg.sweetspi.tests.runtime

import dev.whyoleg.sweetspi.tests.*
import kotlin.test.*

class MultiplatformCrossModuleRuntimeTest : AbstractTest() {
    override val defaultTemplate: TestTemplate get() = TestTemplate.MULTIPLATFORM_MULTIMODULE
    override val defaultVersions: TestVersions get() = TestsArguments.defaultTestVersions

    @Test
    fun test3ModulesInteraction() {
        val project = project {
            append(SETTINGS_GRADLE_KTS) {
                """
                include(":service")    
                include(":serviceProvider")    
                include(":consumer")    
                """.trimIndent()
            }

            folder("service") {
                buildGradleKts(
                    plugins = listOf(
                        "alias(libs.plugins.sweetspi)",
                        "alias(libs.plugins.ksp)",
                        "alias(libs.plugins.kotlin.multiplatform)"
                    ),
                    imports = listOf("dev.whyoleg.sweetspi.gradle.*")
                )
                withSweetSpi()
                allTargets()
                append(BUILD_GRADLE_KTS) {
                    """
                    kotlin {
                        sourceSets.commonMain.dependencies {
                            api(sweetSpiRuntime())
                        }
                    }    
                    """.trimIndent()
                }
                kotlinSourceFile(
                    sourceSet = COMMON_MAIN, path = "main.kt",
                    packageName = "sweettests.multiplatform.service",
                    code = """
                    @Service interface SimpleService
                    """.trimIndent()
                )
            }

            folder("serviceProvider") {
                buildGradleKts(
                    plugins = listOf(
                        "alias(libs.plugins.sweetspi)",
                        "alias(libs.plugins.ksp)",
                        "alias(libs.plugins.kotlin.multiplatform)"
                    ),
                    imports = listOf("dev.whyoleg.sweetspi.gradle.*")
                )
                withSweetSpi()
                allTargets()
                append(BUILD_GRADLE_KTS) {
                    """
                    kotlin {
                        sourceSets.commonMain.dependencies {
                            api(project(":service"))
                        }
                    }
                    """.trimIndent()
                }
                kotlinSourceFile(
                    sourceSet = COMMON_MAIN, path = "main.kt",
                    packageName = "sweettests.multiplatform.serviceProvider",
                    imports = listOf("sweettests.multiplatform.service.*"),
                    code = """
                    @ServiceProvider internal data object SimpleServiceImpl : SimpleService
                    """.trimIndent()
                )
            }

            folder("consumer") {
                // no sweet-spi setup here
                buildGradleKts(plugins = listOf("alias(libs.plugins.kotlin.multiplatform)"))
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
                append(BUILD_GRADLE_KTS) {
                    """
                    kotlin {
                        sourceSets.commonMain.dependencies {
                            implementation(project(":service"))
                            implementation(project(":serviceProvider"))
                        }
                    }
                    """.trimIndent()
                }
                kotlinSourceFile(
                    sourceSet = COMMON_MAIN, path = "main.kt",
                    packageName = "sweettests.multiplatform.consumer",
                    imports = listOf("sweettests.multiplatform.service.*"),
                    code = """
                    val SERVICE = ServiceLoader.load<SimpleService>().single()
                    """.trimIndent()
                )
                kotlinSourceFile(
                    sourceSet = COMMON_TEST, path = "test.kt",
                    packageName = "sweettests.multiplatform.consumer",
                    imports = listOf("sweettests.multiplatform.service.*"),
                    code = """
                    import kotlin.test.*
                    
                    class JvmTest {
                        @Test
                        fun doTest() {
                            assertEquals("SimpleServiceImpl", SERVICE.toString())
                        }
                    }       
                    """.trimIndent()
                )
            }
        }
        project.gradle(":consumer:check") {
            assert(task(":consumer:jvmTest")!!.outcome.isPositive)
            assert(task(":consumer:jsTest")!!.outcome.isPositive)
            assert(task(":consumer:wasmJsTest")!!.outcome.isPositive)
            assert(task(":consumer:wasmWasiTest")!!.outcome.isPositive)

            // tasks are different on different OS, only desktop targets are mentioned
            val nativeTestTasks = setOf(
                ":consumer:macosArm64Test",
                ":consumer:macosX64Test",
                ":consumer:linuxX64Test",
                ":consumer:linuxArm64Test",
                ":consumer:mingwX64Test",
            )
            assert(tasks.any { it.path in nativeTestTasks && it.outcome.isPositive })
        }
    }
}

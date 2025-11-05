/*
 * Copyright (c) 2025 Oleg Yukhnevich. Use of this source code is governed by the Apache 2.0 license.
 */

package dev.whyoleg.sweetspi.tests.processor

import dev.whyoleg.sweetspi.tests.*
import org.gradle.testkit.runner.*
import kotlin.test.*

class MultiplatformProcessorTest : AbstractTest() {
    override val defaultTemplate: TestTemplate get() = TestTemplate.MULTIPLATFORM

    @FastVersionedTest
    fun testClassWithoutDefaultConstructor(versions: TestVersions) {
        val project = project(versions) {

            withSweetSpi()
            allTargets()
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
                @ServiceProvider class SimpleServiceImpl(val a: Int) : SimpleService
                """.trimIndent()
            )
        }
        project.gradle("build", expectFailure = true) {
            assertTrue(taskPaths(TaskOutcome.SUCCESS).none { it.startsWith(":kspKotlin") })
            assertContains(
                output,
                "@ServiceProvider target class 'SimpleServiceImpl' must have a defaultable constructor or be an 'object'"
            )
        }
    }
}
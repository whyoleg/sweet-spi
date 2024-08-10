/*
 * Copyright (c) 2024 Oleg Yukhnevich. Use of this source code is governed by the Apache 2.0 license.
 */

package dev.whyoleg.sweetspi.tests

import org.gradle.testkit.runner.*
import kotlin.test.*

class MultiplatformTest : AbstractTest() {
    override val defaultTemplate: TestTemplate get() = TestTemplate.MULTIPLATFORM
    override val defaultVersions: TestVersions get() = TestsArguments.defaultTestVersions

    @Test
    fun testJvm() {
        val project = project {
            prepend("build.gradle.kts") {
                "import dev.whyoleg.sweetspi.gradle.*"
            }
            append("build.gradle.kts") {
                """
                kotlin {
                  // withSweetSpi()
                  jvm()
                  sourceSets.commonMain.dependencies {
                    api(sweetSpiRuntime())
                  }
                }
                """.trimIndent()
            }
            fileBuilder("src/jvmMain/kotlin/main.kt") {
                append("package sweettests.multiplatform\n\n")
                append("import dev.whyoleg.sweetspi.*\n\n")
                append("@Service interface SimpleService\n")
                append("@ServiceProvider object SimpleServiceImpl : SimpleService\n")
            }
        }
        // TODO: task
        project.gradleRunner("check").build().apply {
            val paths = taskPaths(TaskOutcome.FAILED)
            assertTrue(paths.isEmpty(), "Tasks failed: ${taskPaths(TaskOutcome.FAILED).joinToString("\n  ", "\n  ")}")
        }
    }
}

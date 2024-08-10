/*
 * Copyright (c) 2024 Oleg Yukhnevich. Use of this source code is governed by the Apache 2.0 license.
 */

package dev.whyoleg.sweetspi.tests.processor

import dev.whyoleg.sweetspi.tests.*
import org.gradle.testkit.runner.*
import kotlin.io.path.*
import kotlin.test.*

class MultiplatformJvmProcessorTest : AbstractTest() {
    override val defaultTemplate: TestTemplate get() = TestTemplate.MULTIPLATFORM
    override val defaultVersions: TestVersions get() = TestsArguments.defaultTestVersions

    @Test
    fun testJvmTargetOutput() {
        val project = project {
            withSweetSpi()
            jvmTarget()
            kotlinSourceFile(
                sourceSet = JVM_MAIN,
                path = "main.kt",
                code = """
                @Service interface SimpleService
                
                @ServiceProvider object SimpleServiceImpl: SimpleService    
                """.trimIndent()
            )
        }
        project.gradle("build") {
            val outputDirectory = projectDirectory.resolve("build/generated/ksp/jvm/jvmMain")
            assert(outputDirectory.exists())
            val kotlinFile = outputDirectory.resolve("kotlin/sweettests/multiplatform/sweettests_multiplatform.kt")
            assert(kotlinFile.exists())
            val resourcesFile = outputDirectory.resolve("resources/META-INF/services/dev.whyoleg.sweetspi.internal.InternalServiceModule")
            assert(resourcesFile.exists())
            assert(resourcesFile.readText() == "sweettests.multiplatform.sweettests_multiplatform")
        }
    }

    @Test
    fun testJvmProviderNotExtendingService() {
        val project = project {
            withSweetSpi()
            jvmTarget()
            kotlinSourceFile(
                sourceSet = JVM_MAIN,
                path = "main.kt",
                code = """
                @Service interface SimpleService
                
                @ServiceProvider object SimpleServiceImpl1    
                @ServiceProvider object SimpleServiceImpl2: CharSequence    
                """.trimIndent()
            )
        }

        project.gradle("build", expectFailure = true) {
            assertEquals(
                listOf(":kspKotlinJvm"),
                taskPaths(TaskOutcome.FAILED)
            )
            assertContains(output, "main.kt:5: No applicable services found for 'SimpleServiceImpl1'")
            assertContains(output, "main.kt:6: No applicable services found for 'SimpleServiceImpl2'")
        }
    }
}

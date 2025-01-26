/*
 * Copyright (c) 2024-2025 Oleg Yukhnevich. Use of this source code is governed by the Apache 2.0 license.
 */

package dev.whyoleg.sweetspi.tests.plugin

import dev.whyoleg.sweetspi.tests.*

class MultiplatformPluginTest : AbstractTest() {
    override val defaultTemplate: TestTemplate get() = TestTemplate.MULTIPLATFORM

    @FullVersionedTest
    fun testAllTargets(versions: TestVersions) {
        val project = project(versions) {
            withSweetSpi()
            allTargets()
            kotlinSourceFile(
                sourceSet = COMMON_MAIN,
                path = "main.kt",
                code = """
                @Service interface SimpleService
                @ServiceProvider object SimpleServiceImpl : SimpleService
                """.trimIndent()
            )
        }
        project.gradle("build") {
            assert(task(":kspKotlinJvm")!!.outcome.isPositive)
            assert(task(":kspKotlinJs")!!.outcome.isPositive)
            assert(task(":kspKotlinWasmJs")!!.outcome.isPositive)
            assert(task(":kspKotlinWasmWasi")!!.outcome.isPositive)

            // tasks are different on different OS, only desktop targets are mentioned
            val nativeTestTasks = setOf(
                ":kspKotlinMacosArm64",
                ":kspKotlinMacosX64",
                ":kspKotlinLinuxX64",
                ":kspKotlinLinuxArm64",
                ":kspKotlinMingwX64",
            )
            assert(tasks.any { it.path in nativeTestTasks && it.outcome.isPositive })
        }
    }
}

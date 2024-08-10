/*
 * Copyright (c) 2024 Oleg Yukhnevich. Use of this source code is governed by the Apache 2.0 license.
 */

package dev.whyoleg.sweetspi.tests.plugin

import dev.whyoleg.sweetspi.tests.*
import org.junit.jupiter.params.*
import org.junit.jupiter.params.provider.*

class MultiplatformJvmPluginTest : AbstractTest() {
    override val defaultTemplate: TestTemplate get() = TestTemplate.MULTIPLATFORM

    @ParameterizedTest
    @ArgumentsSource(TestVersionsProvider.All::class)
    fun testJvm(versions: TestVersions) {
        val project = project(versions = versions) {
            withSweetSpi()
            jvmTarget()
            kotlinSourceFile(
                sourceSet = JVM_MAIN,
                path = "main.kt",
                code = """
                @Service interface SimpleService
                @ServiceProvider object SimpleServiceImpl : SimpleService
                """.trimIndent()
            )
        }
        project.gradle("build") {
            assert(task(":kspKotlinJvm")!!.outcome.isPositive)
        }
    }
}

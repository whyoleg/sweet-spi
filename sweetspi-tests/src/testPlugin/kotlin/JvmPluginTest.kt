/*
 * Copyright (c) 2024 Oleg Yukhnevich. Use of this source code is governed by the Apache 2.0 license.
 */

package dev.whyoleg.sweetspi.tests.plugin

import dev.whyoleg.sweetspi.tests.*
import org.gradle.testkit.runner.*
import org.junit.jupiter.params.*
import org.junit.jupiter.params.provider.*

class JvmPluginTest : AbstractTest() {
    override val defaultTemplate: TestTemplate get() = TestTemplate.JVM

    @ParameterizedTest
    @ArgumentsSource(TestVersionsProvider.All::class)
    fun testKspIsExecuted(versions: TestVersions) {
        val project = project(versions = versions) {
            withSweetSpi()
            kotlinSourceFile(
                sourceSet = MAIN,
                path = "main.kt",
                code = """
                @Service interface SimpleService
                @ServiceProvider object SimpleServiceImpl : SimpleService
                """.trimIndent()
            )
        }
        project.gradle("build") {
            assert(task(":kspKotlin")!!.outcome.isPositive)
            // there is shared configuration `ksp` which is default and cause applies KSP to tests
            assert(task(":kspTestKotlin")!!.outcome == TaskOutcome.NO_SOURCE)
        }
    }
}

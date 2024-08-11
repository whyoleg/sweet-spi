/*
 * Copyright (c) 2024 Oleg Yukhnevich. Use of this source code is governed by the Apache 2.0 license.
 */

package dev.whyoleg.sweetspi.tests.runtime

import dev.whyoleg.sweetspi.tests.*
import kotlin.test.*

class JvmRuntimeTest : AbstractTest() {
    override val defaultTemplate: TestTemplate get() = TestTemplate.JVM
    override val defaultVersions: TestVersions get() = TestsArguments.defaultTestVersions

    @Test
    fun testRuntimeWorks() {
        val project = project {
            withSweetSpi()
            kotlinJvmTest()
            kotlinSourceFile(
                sourceSet = MAIN, path = "main.kt",
                code = """
                @Service interface SimpleService
                @ServiceProvider object SimpleServiceImpl : SimpleService
                """.trimIndent()
            )
            kotlinSourceFile(
                sourceSet = TEST, path = "test.kt",
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
            assert(task(":test")!!.outcome.isPositive)
        }
    }
}

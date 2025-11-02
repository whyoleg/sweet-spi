/*
 * Copyright (c) 2024-2025 Oleg Yukhnevich. Use of this source code is governed by the Apache 2.0 license.
 */

package dev.whyoleg.sweetspi.tests.runtime

import dev.whyoleg.sweetspi.tests.*

class JvmRuntimeTest : AbstractTest() {
    override val defaultTemplate: TestTemplate get() = TestTemplate.JVM

    @FastVersionedTest
    fun testRuntimeWorks(versions: TestVersions) {
        val project = project(versions) {
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

    @FastVersionedTest
    fun testJvmServicesAreIncluded(versions: TestVersions) {
        val project = project(versions) {
            withSweetSpi()
            kotlinJvmTest()
            file("src/$MAIN/resources/META-INF/services/sweettests.multiplatform.SimpleService") {
                "sweettests.multiplatform.SimpleServiceImpl2"
            }
            kotlinSourceFile(
                sourceSet = MAIN, path = "main.kt",
                code = """
                @Service interface SimpleService
                @ServiceProvider object SimpleServiceImpl : SimpleService
                class SimpleServiceImpl2 : SimpleService
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
                        assertEquals(2, services.size)
                        assertEquals(SimpleServiceImpl, services[0])
                        assertEquals(SimpleServiceImpl2::class, services[1]::class)
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

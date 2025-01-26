/*
 * Copyright (c) 2024-2025 Oleg Yukhnevich. Use of this source code is governed by the Apache 2.0 license.
 */

package dev.whyoleg.sweetspi.tests.runtime

import dev.whyoleg.sweetspi.tests.*
import org.intellij.lang.annotations.*

class MultiplatformJvmRuntimeTest : AbstractTest() {
    override val defaultTemplate: TestTemplate get() = TestTemplate.MULTIPLATFORM

    private fun jvmProject(versions: TestVersions, block: TestFiles.() -> Unit) = project(versions) {
        withSweetSpi()
        jvmTarget()
        kotlinTest(JVM_TEST)
        block()
    }

    private fun TestFiles.jvmMainFile(@Language("kotlin") code: String) {
        kotlinSourceFile(
            sourceSet = JVM_MAIN,
            path = "test.kt",
            code = code
        )
    }

    private fun TestFiles.jvmTestFile(@Language("kotlin") code: String) {
        kotlinSourceFile(
            sourceSet = JVM_TEST,
            path = "test.kt",
            code = """
            import kotlin.test.*
            
            class JvmTest {
                @Test
                fun doTest() {
                    $code
                }
            }
            """.trimIndent()
        )
    }

    private fun TestProject.runJvmTest() {
        gradle(":jvmTest") {
            assert(task(":jvmTest")!!.outcome.isPositive)
        }
    }

    @FastVersionedTest
    fun testSimple(versions: TestVersions) = jvmProject(versions) {
        jvmMainFile(
            """
            @Service interface SimpleService
            @ServiceProvider object SimpleServiceImpl : SimpleService
            """.trimIndent()
        )
        jvmTestFile(
            """
            val services = ServiceLoader.load<SimpleService>()
            assertEquals(1, services.size)
            val service = services.single()
            assertEquals(SimpleServiceImpl, service)
            """.trimIndent()
        )
    }.runJvmTest()

    @FastVersionedTest
    fun testMultipleServices(versions: TestVersions) = jvmProject(versions) {
        jvmMainFile(
            """
            @Service interface SimpleService
            @ServiceProvider object SimpleServiceImpl1 : SimpleService
            @ServiceProvider object SimpleServiceImpl2 : SimpleService
            """.trimIndent()
        )
        jvmTestFile(
            """
            assertEquals(
                setOf(SimpleServiceImpl1, SimpleServiceImpl2),
                ServiceLoader.load<SimpleService>().toSet()
            )
            """.trimIndent()
        )
    }.runJvmTest()

    @FastVersionedTest
    fun testMultipleInheritance(versions: TestVersions) = jvmProject(versions) {
        jvmMainFile(
            """
            @Service interface SimpleService1
            @Service interface SimpleService2

            @ServiceProvider object SimpleServiceImpl : SimpleService1, SimpleService2
            """.trimIndent()
        )
        jvmTestFile(
            """
            assertEquals(
                SimpleServiceImpl,                
                ServiceLoader.load<SimpleService1>().single()
            )
            assertEquals(
                SimpleServiceImpl,                
                ServiceLoader.load<SimpleService1>().single()
            )
            """.trimIndent()
        )
    }.runJvmTest()

    @FastVersionedTest
    fun testFunction(versions: TestVersions) = jvmProject(versions) {
        jvmMainFile(
            """
            @Service interface SimpleService

            @ServiceProvider
            fun createSimpleService(): SimpleService = SimpleServiceImpl()
            class SimpleServiceImpl : SimpleService {
                override fun toString(): String = "SimpleServiceImpl"
            }
            """.trimIndent()
        )
        jvmTestFile(
            """
            val s1 = ServiceLoader.load<SimpleService>().single()
            assertEquals("SimpleServiceImpl", s1.toString())
            val s2 = ServiceLoader.load<SimpleService>().single()
            assertEquals("SimpleServiceImpl", s2.toString())
            assertEquals(s1, s2)
            """.trimIndent()
        )
    }.runJvmTest()

    @FastVersionedTest
    fun testFunctionErrorPropagation(versions: TestVersions) = jvmProject(versions) {
        jvmMainFile(
            """
            @Service interface SimpleService

            @ServiceProvider
            fun createSimpleService(): SimpleService = error("NO")
            """.trimIndent()
        )
        jvmTestFile(
            """
            val cause = assertFailsWith<IllegalStateException> {
                ServiceLoader.load<SimpleService>().single()           
            }
            assertEquals("NO", cause.message)
            """.trimIndent()
        )
    }.runJvmTest()

    @FastVersionedTest
    fun testProperty(versions: TestVersions) = jvmProject(versions) {
        jvmMainFile(
            """
            @Service interface SimpleService

            @ServiceProvider
            val initSimpleService: SimpleService = SimpleServiceImpl()
            class SimpleServiceImpl : SimpleService {
                override fun toString(): String = "SimpleServiceImpl"
            }
            """.trimIndent()
        )
        jvmTestFile(
            """
            val s1 = ServiceLoader.load<SimpleService>().single()
            assertEquals("SimpleServiceImpl", s1.toString())
            val s2 = ServiceLoader.load<SimpleService>().single()
            assertEquals("SimpleServiceImpl", s2.toString())
            assertEquals(s1, s2)
            """.trimIndent()
        )
    }.runJvmTest()
}

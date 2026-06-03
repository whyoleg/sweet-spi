/*
 * Copyright (c) 2026 Oleg Yukhnevich. Use of this source code is governed by the Apache 2.0 license.
 */

package dev.whyoleg.sweetspi.tests

import dev.whyoleg.sweetspi.tests.api.*
import org.intellij.lang.annotations.*
import kotlin.test.*

// Single-module execution across every supported @Provides shape (object, class with various
// constructors, top-level/object/companion function & property, generic service). A dedicated jvm-only
// case plus the canonical kmp matrix (see multiplatformExecutionBuildFile).
class ProviderFormsTest : TestProjectBasedTest() {

    @Test
    fun testJvm() = context(project) {
        bootstrapKotlinJvmProject()
        file("src/main/kotlin/SimpleService.kt").kotlin(
            """
            import dev.whyoleg.sweetspi.*
            @Service interface SimpleService
            @Provides(SimpleService::class) object SimpleServiceImpl : SimpleService
            """.trimIndent()
        )
        file("src/test/kotlin/SimpleTest.kt").kotlin(
            """
            import dev.whyoleg.sweetspi.*
            import kotlin.test.*

            class SimpleTest {
                @Test
                fun test() {
                    val providers = ServiceLoader.load<SimpleService>().toList()
                    assertEquals(1, providers.size)
                    val provider = providers.single()
                    assertEquals(SimpleServiceImpl::class, provider.type)
                    assertEquals(false, provider.isInitialized())
                    assertEquals(SimpleServiceImpl, provider.value)
                    assertEquals(true, provider.isInitialized())
                }
            }
            """.trimIndent()
        )

        gradle(":updateKotlinAbi", ":test") {
            assertTrue(task(":test")!!.outcome.isPositive)
        }
    }

    @Test
    fun testObject() = testSingleSimpleService(
        mainCode = """
        @Provides(SimpleService::class) object SimpleServiceImpl : SimpleService
        """.trimIndent(),
        testCode = """
        assertEquals(SimpleServiceImpl::class, provider.type)
        assertEquals(false, provider.isInitialized())
        assertEquals(SimpleServiceImpl, provider.value)
        assertEquals(true, provider.isInitialized())
        """.trimIndent()
    )

    @Test
    fun testClass_primaryConstructor() = testSingleSimpleService(
        mainCode = """
        @Provides(SimpleService::class) class SimpleServiceImpl : SimpleService {
            override fun toString() = "SimpleServiceImpl"
        }
        """.trimIndent(),
        testCode = """
        assertEquals(SimpleServiceImpl::class, provider.type)
        assertEquals(false, provider.isInitialized())
        assertEquals("SimpleServiceImpl", provider.value.toString())
        assertEquals(true, provider.isInitialized())
        """.trimIndent()
    )

    @Test
    fun testClass_secondaryConstructor() = testSingleSimpleService(
        mainCode = $$"""
        @Provides(SimpleService::class) class SimpleServiceImpl(val string: String) : SimpleService {
            constructor(): this("default")
            override fun toString() = "SimpleServiceImpl($string)"
        }
        """.trimIndent(),
        testCode = """
        assertEquals(SimpleServiceImpl::class, provider.type)
        assertEquals(false, provider.isInitialized())
        assertEquals("SimpleServiceImpl(default)", provider.value.toString())
        assertEquals(true, provider.isInitialized())
        """.trimIndent()
    )

    @Test
    fun testClass_constructorWithDefaultParameters() = testSingleSimpleService(
        mainCode = $$"""
        @Provides(SimpleService::class) class SimpleServiceImpl(val string: String = "default") : SimpleService {
            override fun toString() = "SimpleServiceImpl($string)"
        }
        """.trimIndent(),
        testCode = """
        assertEquals(SimpleServiceImpl::class, provider.type)
        assertEquals(false, provider.isInitialized())
        assertEquals("SimpleServiceImpl(default)", provider.value.toString())
        assertEquals(true, provider.isInitialized())
        """.trimIndent()
    )

    @Test
    fun testFunction_simple() = testSingleSimpleService(
        mainCode = $$"""
        class SimpleServiceImpl(val string: String) : SimpleService {
            override fun toString() = "SimpleServiceImpl($string)"
        }
        @Provides(SimpleService::class) fun provideSimpleService() = SimpleServiceImpl("default")
        """.trimIndent(),
        testCode = """
        assertEquals(SimpleServiceImpl::class, provider.type)
        assertEquals(false, provider.isInitialized())
        assertEquals("SimpleServiceImpl(default)", provider.value.toString())
        assertEquals(true, provider.isInitialized())
        """.trimIndent()
    )

    @Test
    fun testFunction_defaultParameters() = testSingleSimpleService(
        mainCode = $$"""
        class SimpleServiceImpl(val string: String) : SimpleService {
            override fun toString() = "SimpleServiceImpl($string)"
        }
        @Provides(SimpleService::class) fun provideSimpleService(string: String = "default") = SimpleServiceImpl(string)
        """.trimIndent(),
        testCode = """
        assertEquals(SimpleServiceImpl::class, provider.type)
        assertEquals(false, provider.isInitialized())
        assertEquals("SimpleServiceImpl(default)", provider.value.toString())
        assertEquals(true, provider.isInitialized())
        """.trimIndent()
    )

    @Test
    fun testProperty() = testSingleSimpleService(
        mainCode = $$"""
        class SimpleServiceImpl(val string: String) : SimpleService {
            override fun toString() = "SimpleServiceImpl($string)"
        }
        @Provides(SimpleService::class) val lazyService = SimpleServiceImpl("default")
        """.trimIndent(),
        testCode = """
        assertEquals(SimpleServiceImpl::class, provider.type)
        assertEquals(false, provider.isInitialized())
        assertEquals("SimpleServiceImpl(default)", provider.value.toString())
        assertEquals(true, provider.isInitialized())
        """.trimIndent()
    )

    @Test
    fun testFunction_inObject() = testSingleSimpleService(
        mainCode = $$"""
        class SimpleServiceImpl(val string: String) : SimpleService {
            override fun toString() = "SimpleServiceImpl($string)"
        }
        object Provider {
            @Provides(SimpleService::class) fun provide() = SimpleServiceImpl("default")
        }
        """.trimIndent(),
        testCode = """
        assertEquals(SimpleServiceImpl::class, provider.type)
        assertEquals("SimpleServiceImpl(default)", provider.value.toString())
        """.trimIndent()
    )

    @Test
    fun testProperty_inObject() = testSingleSimpleService(
        mainCode = $$"""
        class SimpleServiceImpl(val string: String) : SimpleService {
            override fun toString() = "SimpleServiceImpl($string)"
        }
        object Provider {
            @Provides(SimpleService::class) val service = SimpleServiceImpl("default")
        }
        """.trimIndent(),
        testCode = """
        assertEquals(SimpleServiceImpl::class, provider.type)
        assertEquals("SimpleServiceImpl(default)", provider.value.toString())
        """.trimIndent()
    )

    @Test
    fun testFunction_inCompanionObject() = testSingleSimpleService(
        mainCode = $$"""
        class SimpleServiceImpl(val string: String) : SimpleService {
            override fun toString() = "SimpleServiceImpl($string)"
        }
        class Host {
            companion object {
                @Provides(SimpleService::class) fun provide() = SimpleServiceImpl("default")
            }
        }
        """.trimIndent(),
        testCode = """
        assertEquals(SimpleServiceImpl::class, provider.type)
        assertEquals("SimpleServiceImpl(default)", provider.value.toString())
        """.trimIndent()
    )

    @Test
    fun testGenericService() = testSimpleServiceMultiplatform(
        mainCode = """
        @Service abstract class SimpleService<T>(val value: T)
        @Provides(SimpleService::class) object SimpleServiceString : SimpleService<String>("100")
        @Provides(SimpleService::class) object SimpleServiceInt : SimpleService<Int>(111)
        """.trimIndent(),
        testCode = """
        val providers = ServiceLoader.load<SimpleService<*>>().toList()
        assertEquals(2, providers.size)
        assertEquals(listOf("100", "111"), providers.map { it.value.value.toString() }.sorted())
        """.trimIndent()
    )

    private fun testSingleSimpleService(
        @Language("kotlin") mainCode: String,
        @Language("kotlin") testCode: String, // has `provider` in scope
    ) = testSimpleServiceMultiplatform(
        mainCode = """
        |@Service interface SimpleService
        |${mainCode.alignMargin()}
        """.trimMargin(),
        testCode = """
        |val providers = ServiceLoader.load<SimpleService>().toList()
        |assertEquals(1, providers.size)
        |val provider = providers.single()
        |${testCode.alignMargin()}
        """.trimMargin()
    )

    // KMP: the provider declaration (mainCode) goes in commonMain, the load+asserts (testCode) in
    // commonTest; kmpExecutionScenario runs it on every canonical target.
    private fun testSimpleServiceMultiplatform(
        @Language("kotlin") mainCode: String,
        @Language("kotlin") testCode: String,
    ) = context(project) {
        kmpExecutionScenario {
            commonMain(mainCode)
            commonTest(testCode)
        }
    }
}

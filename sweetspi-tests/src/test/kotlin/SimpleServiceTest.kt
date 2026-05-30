/*
 * Copyright (c) 2026 Oleg Yukhnevich. Use of this source code is governed by the Apache 2.0 license.
 */

package dev.whyoleg.sweetspi.tests

import dev.whyoleg.sweetspi.tests.api.*
import org.intellij.lang.annotations.*
import kotlin.io.path.*
import kotlin.test.*

class SimpleServiceTest : TestProjectBasedTest() {

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

    private fun testSimpleServiceMultiplatform(
        @Language("kotlin") mainCode: String,
        @Language("kotlin") testCode: String,
    ) = context(project) {
        bootstrapKotlinMultiplatformProject()
        file("build.gradle.kts").appendText(
            """
            kotlin {
                jvm()
                macosArm64()
                js { nodejs() }
                wasmJs { nodejs() }
                wasmWasi { nodejs() }
            }
            """.trimIndent()
        )

        file("src/commonMain/kotlin/SimpleService.kt").kotlin(
            """
            |import dev.whyoleg.sweetspi.*
            |
            |${mainCode.alignMargin()}
            |
            |""".trimMargin()
        )
        file("src/commonTest/kotlin/SimpleServiceTest.kt").kotlin(
            """
            |import dev.whyoleg.sweetspi.*
            |import kotlin.test.*
            |                
            |class SimpleServiceTest {
            |    @Test
            |    fun test() {
            |        ${testCode.alignMargin("|        ")}
            |    }
            |}
            |
            """.trimMargin()
        )

        // TODO: ":updateKotlinAbi :check" causes:
        //  need to reproduce and report it
//        A problem was found with the configuration of task ':updateKotlinAbi' (type 'KotlinAbiUpdateTask').
//        - Gradle detected a problem with the following location: '/Users/Oleg.Yukhnevich/Projects/whyoleg/sweet-spi/sweetspi-tests/build/test-projects/dev.whyoleg.sweetspi.tests.SimpleServiceTest/testMultiplatform/api'.
//
//        Reason: Task ':checkKotlinAbi' uses this output of task ':updateKotlinAbi' without declaring an explicit or implicit dependency. This can lead to incorrect results being produced, depending on what order the tasks are executed.
//
//        Possible solutions:
//        1. Declare task ':updateKotlinAbi' as an input of ':checkKotlinAbi'.
//        2. Declare an explicit dependency on ':updateKotlinAbi' from ':checkKotlinAbi' using Task#dependsOn.
//        3. Declare an explicit dependency on ':updateKotlinAbi' from ':checkKotlinAbi' using Task#mustRunAfter.
//
//        For more information, please refer to https://docs.gradle.org/9.4.1/userguide/validation_problems.html#implicit_dependency in the Gradle documentation.

        gradle(":updateKotlinAbi", forwardOutput = false)
        gradle(/*":updateKotlinAbi", */":check") {
            assertTrue(task(":jvmTest")!!.outcome.isPositive)
            assertTrue(task(":macosArm64Test")!!.outcome.isPositive)
            assertTrue(task(":jsNodeTest")!!.outcome.isPositive)
            assertTrue(task(":wasmJsNodeTest")!!.outcome.isPositive)
            assertTrue(task(":wasmWasiNodeTest")!!.outcome.isPositive)
        }
    }
}

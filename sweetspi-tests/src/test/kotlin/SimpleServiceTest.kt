/*
 * Copyright (c) 2026 Oleg Yukhnevich. Use of this source code is governed by the Apache 2.0 license.
 */

package dev.whyoleg.sweetspi.tests

import dev.whyoleg.sweetspi.tests.api.*
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
    fun testMultiplatform() = context(project) {
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
            import dev.whyoleg.sweetspi.*
            @Service interface SimpleService
            @Provides(SimpleService::class) object SimpleServiceImpl : SimpleService
            """.trimIndent()
        )
        file("src/commonTest/kotlin/SimpleTest.kt").kotlin(
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

        gradle(":updateKotlinAbi", ":check") {
            assertTrue(task(":jvmTest")!!.outcome.isPositive)
            assertTrue(task(":macosArm64Test")!!.outcome.isPositive)
            assertTrue(task(":jsNodeTest")!!.outcome.isPositive)
            assertTrue(task(":wasmJsNodeTest")!!.outcome.isPositive)
            assertTrue(task(":wasmWasiNodeTest")!!.outcome.isPositive)
        }
    }
}

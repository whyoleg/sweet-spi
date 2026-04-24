/*
 * Copyright (c) 2026 Oleg Yukhnevich. Use of this source code is governed by the Apache 2.0 license.
 */

package dev.whyoleg.sweetspi.tests

import dev.whyoleg.sweetspi.tests.api.*
import kotlin.test.*

class SimpleServiceTest : TestProjectBasedTest() {
    @Test
    fun test() = context(project) {
        bootstrapKotlinJvmProject()
        file("src/main/kotlin/SimpleService.kt").kotlin(
            """
            import dev.whyoleg.sweetspi.*
            @Service interface SimpleService
            @ServiceProvider(SimpleService::class) object SimpleServiceImpl : SimpleService
            """.trimIndent()
        )
        file("src/test/kotlin/SimpleTest.kt").kotlin(
            """
            import dev.whyoleg.sweetspi.*
            import kotlin.test.*
                            
            class SimpleTest {
                @Test
                fun testServices() {
                    val services = ServiceLoader.load<SimpleService>().toList()
                    assertEquals(1, services.size)
                    val service = services.single()
                    assertEquals(SimpleServiceImpl, service)
                }

                @Test
                fun testProviders() {
                    val providers = ServiceLoader.loadProviders<SimpleService>().toList()
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
}

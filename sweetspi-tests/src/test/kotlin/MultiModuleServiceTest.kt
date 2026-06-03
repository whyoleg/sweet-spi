/*
 * Copyright (c) 2026 Oleg Yukhnevich. Use of this source code is governed by the Apache 2.0 license.
 */

package dev.whyoleg.sweetspi.tests

import dev.whyoleg.sweetspi.tests.api.*
import kotlin.io.path.*
import kotlin.test.*

class MultiModuleServiceTest : TestProjectBasedTest() {

    // :consumer depends only on :provider; the service type reaches it transitively via
    // :provider's `api(project(":service"))` — the "library" promise.
    @Test
    fun testJvm() = context(project) {
        bootstrapMultiModuleProject(listOf("service", "provider", "consumer"))

        jvmBuildFile("service")
        file("service/src/main/kotlin/GreetingService.kt").kotlin(
            "sweettests.service",
            """
            import dev.whyoleg.sweetspi.*
            @Service interface GreetingService { fun greet(): String }
            """.trimIndent()
        )

        jvmBuildFile("provider").appendText(
            """
            dependencies { api(project(":service")) }
            """.trimIndent()
        )
        file("provider/src/main/kotlin/EnglishGreeting.kt").kotlin(
            "sweettests.provider",
            """
            import dev.whyoleg.sweetspi.*
            import sweettests.service.*
            @Provides(GreetingService::class) object EnglishGreeting : GreetingService {
                override fun greet(): String = "hello"
            }
            """.trimIndent()
        )

        jvmBuildFile("consumer").appendText(
            """
            dependencies { implementation(project(":provider")) }
            """.trimIndent()
        )
        file("consumer/src/test/kotlin/ConsumerTest.kt").kotlin(
            "sweettests.consumer",
            """
            import dev.whyoleg.sweetspi.*
            import sweettests.service.*
            import kotlin.test.*
            class ConsumerTest {
                @Test
                fun test() {
                    val providers = ServiceLoader.load<GreetingService>().toList()
                    assertEquals(1, providers.size)
                    assertEquals("hello", providers.single().value.greet())
                }
            }
            """.trimIndent()
        )

        gradle(":consumer:test") {
            assertTrue(task(":consumer:test")!!.outcome.isPositive)
        }
    }
}

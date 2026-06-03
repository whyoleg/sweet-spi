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
    //
    // KNOWN-FAILING (G8): cross-module discovery is broken on JVM — `$ServiceProvider` is generated in
    // IR, so it is not in the consumable ABI and `:provider`'s `@Provides` cannot resolve it
    // (`Provider class not found for service type`). This test encodes the requirement; M1 fixes it by
    // generating `$ServiceProvider` in FIR, then removes this @Ignore. See
    // docs/superpowers/specs/2026-06-02-roadmap-design.md (M1 / G8).
    @Ignore
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

    // KNOWN-FAILING (G8): same cross-module gap as testJvm — the jvm() target fails identically at
    // `:provider:compileKotlinJvm`. Encodes the KMP cross-module requirement; M1 removes this @Ignore.
    // See docs/superpowers/specs/2026-06-02-roadmap-design.md (M1 / G8).
    @Ignore
    @Test
    fun testMultiplatform() = context(project) {
        bootstrapMultiModuleProject(listOf("service", "provider", "consumer"))
        val targets = listOf("jvm", "macosArm64")

        multiplatformBuildFile("service", targets = targets)
        file("service/src/commonMain/kotlin/GreetingService.kt").kotlin(
            "sweettests.service",
            """
            import dev.whyoleg.sweetspi.*
            @Service interface GreetingService { fun greet(): String }
            """.trimIndent()
        )

        multiplatformBuildFile("provider", targets = targets).appendText(
            """
            kotlin { sourceSets.commonMain.dependencies { api(project(":service")) } }
            """.trimIndent()
        )
        file("provider/src/commonMain/kotlin/EnglishGreeting.kt").kotlin(
            "sweettests.provider",
            """
            import dev.whyoleg.sweetspi.*
            import sweettests.service.*
            @Provides(GreetingService::class) object EnglishGreeting : GreetingService {
                override fun greet(): String = "hello"
            }
            """.trimIndent()
        )

        multiplatformBuildFile("consumer", targets = targets).appendText(
            """
            kotlin { sourceSets.commonMain.dependencies { implementation(project(":provider")) } }
            """.trimIndent()
        )
        file("consumer/src/commonTest/kotlin/ConsumerTest.kt").kotlin(
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

        gradle(":consumer:jvmTest", ":consumer:macosArm64Test") {
            assertTrue(task(":consumer:jvmTest")!!.outcome.isPositive)
            assertTrue(task(":consumer:macosArm64Test")!!.outcome.isPositive)
        }
    }
}

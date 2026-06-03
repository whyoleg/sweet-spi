/*
 * Copyright (c) 2026 Oleg Yukhnevich. Use of this source code is governed by the Apache 2.0 license.
 */

package dev.whyoleg.sweetspi.tests

import org.intellij.lang.annotations.*

// Shared single-service fixture (package `sweettests`) used by topology tests (placement, cross-module).
// Service, provider and consumer are split so each can be dropped into a different source set / module.
object GreetingFixture {
    @Language("kotlin")
    val service = """
        import dev.whyoleg.sweetspi.*
        @Service interface GreetingService { fun greet(): String }
    """.trimIndent()

    @Language("kotlin")
    val provider = """
        import dev.whyoleg.sweetspi.*
        @Provides(GreetingService::class) object EnglishGreeting : GreetingService {
            override fun greet(): String = "hello"
        }
    """.trimIndent()

    @Language("kotlin")
    val consumerTest = """
        import dev.whyoleg.sweetspi.*
        import kotlin.test.*
        class GreetingTest {
            @Test
            fun test() {
                val providers = ServiceLoader.load<GreetingService>().toList()
                assertEquals(1, providers.size)
                assertEquals("hello", providers.single().value.greet())
            }
        }
    """.trimIndent()
}

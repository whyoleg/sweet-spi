/*
 * Copyright (c) 2026 Oleg Yukhnevich. Use of this source code is governed by the Apache 2.0 license.
 */

package dev.whyoleg.sweetspi.tests

import dev.whyoleg.sweetspi.tests.api.*
import kotlin.test.*

// Multiple providers of one service, discovered together, distributed at each granularity: same file,
// different files of one module, and different modules (consumer depends on both). Verifies the loader
// merges registrations from every place on the canonical KMP matrix (incl. several separately-linked
// modules on native/wasm).
class MultipleProvidersTest : TestProjectBasedTest() {

    @Test
    fun sameFile() = context(project) {
        kmpExecutionScenario {
            commonMain(
                """
                @Service interface S { fun id(): Int }
                @Provides(S::class) object A : S { override fun id() = 1 }
                @Provides(S::class) object B : S { override fun id() = 2 }
                """.trimIndent()
            )
            commonTest(
                """
                val ids = ServiceLoader.load<S>().map { it.value.id() }.toSet()
                assertEquals(setOf(1, 2), ids)
                """.trimIndent()
            )
        }
    }

    @Test
    fun differentFilesSameModule() = context(project) {
        kmpExecutionScenario {
            commonMain("@Service interface S { fun id(): Int }")
            commonMain("@Provides(S::class) object A : S { override fun id() = 1 }")
            commonMain("@Provides(S::class) object B : S { override fun id() = 2 }")
            commonTest(
                """
                val ids = ServiceLoader.load<S>().map { it.value.id() }.toSet()
                assertEquals(setOf(1, 2), ids)
                """.trimIndent()
            )
        }
    }

    @Test
    fun differentModules() = context(project) {
        kmpExecutionScenario {
            module("service") { commonMain("@Service interface S { fun id(): Int }") }
            module("p1", dependsOn = listOf(":service")) {
                commonMain("@Provides(S::class) object A : S { override fun id() = 1 }")
            }
            module("p2", dependsOn = listOf(":service")) {
                commonMain("@Provides(S::class) object B : S { override fun id() = 2 }")
            }
            module("consumer", dependsOn = listOf(":p1", ":p2")) {
                commonTest(
                    """
                    val ids = ServiceLoader.load<S>().map { it.value.id() }.toSet()
                    assertEquals(setOf(1, 2), ids)
                    """.trimIndent()
                )
            }
        }
    }
}

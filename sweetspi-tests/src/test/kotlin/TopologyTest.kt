/*
 * Copyright (c) 2026 Oleg Yukhnevich. Use of this source code is governed by the Apache 2.0 license.
 */

package dev.whyoleg.sweetspi.tests

import dev.whyoleg.sweetspi.tests.api.*
import kotlin.test.*

// Where @Service / @Provides / the loader live, across BOTH module boundaries and source-set boundaries,
// on the canonical KMP matrix. Each scenario fixes the code and varies only placement, so the placement
// is the visible variable. Cross-module SPI works on jvm/native/wasmJs/wasmWasi (js is a known
// limitation, returns with the js target later). JVM-only (plain kotlin-jvm) topology is deferred to M2.
class TopologyTest : TestProjectBasedTest() {

    // --- cross-module (module relation) × source-set relation ---

    @Test
    fun crossModuleCommon() = context(project) {
        kmpExecutionScenario {
            module("service") { commonMain("@Service interface S { fun greet(): String }") }
            module("provider", api = ":service") {
                commonMain("""@Provides(S::class) object Impl : S { override fun greet() = "hello" }""")
            }
            module("consumer", implementation = ":provider") {
                commonTest(
                    """
                    val ps = ServiceLoader.load<S>().toList()
                    assertEquals(1, ps.size)
                    assertEquals("hello", ps.single().value.greet())
                    """.trimIndent()
                )
            }
        }
    }

    @Test
    fun crossModuleNonJvm() = context(project) {
        kmpExecutionScenario {
            module("service") { commonMain("@Service interface S { fun greet(): String }") }
            module("provider", api = ":service") {
                nonJvmMain("""@Provides(S::class) object Impl : S { override fun greet() = "hello" }""")
            }
            module("consumer", implementation = ":provider") {
                nonJvmTest(
                    """
                    val ps = ServiceLoader.load<S>().toList()
                    assertEquals(1, ps.size)
                    assertEquals("hello", ps.single().value.greet())
                    """.trimIndent()
                )
            }
        }
    }

    @Test
    fun crossModuleJvmAndNative() = context(project) {
        kmpExecutionScenario {
            module("service") { commonMain("@Service interface S { fun greet(): String }") }
            module("provider", api = ":service") {
                jvmAndNativeMain("""@Provides(S::class) object Impl : S { override fun greet() = "hello" }""")
            }
            module("consumer", implementation = ":provider") {
                jvmAndNativeTest(
                    """
                    val ps = ServiceLoader.load<S>().toList()
                    assertEquals(1, ps.size)
                    assertEquals("hello", ps.single().value.greet())
                    """.trimIndent()
                )
            }
        }
    }

    // --- single-module, intermediate source-set placement (source-set relation only) ---

    @Test
    fun nonJvmPlacement() = context(project) {
        kmpExecutionScenario {
            nonJvmMain("@Service interface S { fun greet(): String }")
            nonJvmMain("""@Provides(S::class) object Impl : S { override fun greet() = "hello" }""")
            nonJvmTest(
                """
                val ps = ServiceLoader.load<S>().toList()
                assertEquals(1, ps.size)
                assertEquals("hello", ps.single().value.greet())
                """.trimIndent()
            )
        }
    }

    @Test
    fun jvmAndNativePlacement() = context(project) {
        kmpExecutionScenario {
            jvmAndNativeMain("@Service interface S { fun greet(): String }")
            jvmAndNativeMain("""@Provides(S::class) object Impl : S { override fun greet() = "hello" }""")
            jvmAndNativeTest(
                """
                val ps = ServiceLoader.load<S>().toList()
                assertEquals(1, ps.size)
                assertEquals("hello", ps.single().value.greet())
                """.trimIndent()
            )
        }
    }
}

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
            module("provider", dependsOn = listOf(":service")) {
                commonMain("""@Provides(S::class) object Impl : S { override fun greet() = "hello" }""")
            }
            module("consumer", dependsOn = listOf(":provider")) {
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
            module("provider", dependsOn = listOf(":service")) {
                nonJvmMain("""@Provides(S::class) object Impl : S { override fun greet() = "hello" }""")
            }
            module("consumer", dependsOn = listOf(":provider")) {
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
            module("provider", dependsOn = listOf(":service")) {
                jvmAndNativeMain("""@Provides(S::class) object Impl : S { override fun greet() = "hello" }""")
            }
            module("consumer", dependsOn = listOf(":provider")) {
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

    // --- single-module, file granularity (one commonMain(...) call == one file) ---

    @Test
    fun sameFile() = context(project) {
        kmpExecutionScenario {
            // service + provider in a single commonMain file
            commonMain(
                """
                @Service interface S { fun greet(): String }
                @Provides(S::class) object Impl : S { override fun greet() = "hello" }
                """.trimIndent()
            )
            commonTest(
                """
                val ps = ServiceLoader.load<S>().toList()
                assertEquals(1, ps.size)
                assertEquals("hello", ps.single().value.greet())
                """.trimIndent()
            )
        }
    }

    @Test
    fun differentFilesSameSourceSet() = context(project) {
        kmpExecutionScenario {
            // two commonMain(...) calls -> two separate files in the same source set
            commonMain("@Service interface S { fun greet(): String }")
            commonMain("""@Provides(S::class) object Impl : S { override fun greet() = "hello" }""")
            commonTest(
                """
                val ps = ServiceLoader.load<S>().toList()
                assertEquals(1, ps.size)
                assertEquals("hello", ps.single().value.greet())
                """.trimIndent()
            )
        }
    }

    @Test
    fun differentSourceSetsSameModule() = context(project) {
        kmpExecutionScenario {
            // service in commonMain, provider in the nonJvm intermediate of the SAME module
            commonMain("@Service interface S { fun greet(): String }")
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
}

/*
 * Copyright (c) 2026 Oleg Yukhnevich. Use of this source code is governed by the Apache 2.0 license.
 */

package dev.whyoleg.sweetspi.tests

import dev.whyoleg.sweetspi.tests.api.*
import kotlin.test.*

// JVM compat-mode generation: which META-INF/services file is written, with which entries, per
// @Service(javaServiceLoaderCompatibilityMode). NO/PROVIDE generate identically (X$ServiceProvider);
// FULL lists the user class directly under X so plain java.util.ServiceLoader works.
class CompatModeTest : TestProjectBasedTest() {

    @Test
    fun fullCompatibility_listsUserClassUnderServiceId() = context(project) {
        jvmExecutionScenario {
            main(
                """
                @Service(JavaServiceLoaderCompatibilityMode.FULL_COMPATIBILITY)
                interface S
                @Provides(S::class) class Impl : S
                """.trimIndent()
            )
            test(
                """
                val providers = ServiceLoader.load<S>().toList()
                assertEquals(1, providers.size)
                assertTrue(providers.single().value is Impl)
                """.trimIndent()
            )
        }
        assertServicesFile(serviceId = "sweettests.S", entries = setOf("sweettests.Impl"))
        assertNoServicesFile(serviceId = "sweettests.S\$\$ServiceProvider")
    }
}

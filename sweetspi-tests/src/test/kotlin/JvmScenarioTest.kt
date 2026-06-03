/*
 * Copyright (c) 2026 Oleg Yukhnevich. Use of this source code is governed by the Apache 2.0 license.
 */

package dev.whyoleg.sweetspi.tests

import dev.whyoleg.sweetspi.tests.api.*
import kotlin.test.*

// Validates the JvmScenario DSL against current (NO_COMPATIBILITY) behavior + an existing diagnostic.
class JvmScenarioTest : TestProjectBasedTest() {

    @Test
    fun executionSingleModule() = context(project) {
        jvmExecutionScenario {
            main(
                """
                @Service interface S
                @Provides(S::class) object Impl : S
                """.trimIndent()
            )
            test(
                """
                assertEquals(1, ServiceLoader.load<S>().toList().size)
                """.trimIndent()
            )
        }
    }

    @Test
    fun checkerDiagnostic() = context(project) {
        jvmCheckerScenario("@Service class NotAService") {
            failsWith("@Service is only applicable to interfaces or abstract classes")
        }
    }
}

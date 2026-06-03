/*
 * Copyright (c) 2026 Oleg Yukhnevich. Use of this source code is governed by the Apache 2.0 license.
 */

package dev.whyoleg.sweetspi.tests

import dev.whyoleg.sweetspi.tests.api.*
import kotlin.test.*

// FULL_COMPATIBILITY only supports a non-object class provider (java.util.ServiceLoader needs a nameable,
// instantiable class). Objects, functions and properties are rejected. NO/PROVIDE keep every form.
class CompatModeCheckerTest : TestProjectBasedTest() {

    private val fullService = "@Service(JavaServiceLoaderCompatibilityMode.FULL_COMPATIBILITY) interface S"
    private val expected = "FULL_COMPATIBILITY"

    @Test
    fun fullRejectsObject() = context(project) {
        jvmCheckerScenario(
            """
            $fullService
            @Provides(S::class) object Impl : S
            """.trimIndent()
        ) { failsWith(expected) }
    }

    @Test
    fun fullRejectsFunction() = context(project) {
        jvmCheckerScenario(
            """
            $fullService
            class ImplC : S
            @Provides(S::class) fun provide(): S = ImplC()
            """.trimIndent()
        ) { failsWith(expected) }
    }

    @Test
    fun fullRejectsProperty() = context(project) {
        jvmCheckerScenario(
            """
            $fullService
            class ImplC : S
            @Provides(S::class) val provide: S = ImplC()
            """.trimIndent()
        ) { failsWith(expected) }
    }

    @Test
    fun fullAcceptsClass() = context(project) {
        jvmCheckerScenario(
            """
            $fullService
            @Provides(S::class) class Impl : S
            """.trimIndent()
        ) { compiles() }
    }

    @Test
    fun noCompatibilityAcceptsObject() = context(project) {
        jvmCheckerScenario(
            """
            @Service interface S
            @Provides(S::class) object Impl : S
            """.trimIndent()
        ) { compiles() }
    }
}

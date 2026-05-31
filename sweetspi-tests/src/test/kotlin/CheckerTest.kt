/*
 * Copyright (c) 2026 Oleg Yukhnevich. Use of this source code is governed by the Apache 2.0 license.
 */

package dev.whyoleg.sweetspi.tests

import dev.whyoleg.sweetspi.tests.api.*
import org.intellij.lang.annotations.*
import kotlin.test.*

class CheckerTest : TestProjectBasedTest() {

    private fun assertCompilationError(
        @Language("kotlin") code: String,
        expectedMessage: String,
    ) = context(project) {
        bootstrapKotlinJvmProject()
        file("src/main/kotlin/Subject.kt").kotlin(
            """
            |import dev.whyoleg.sweetspi.*
            |
            |${code.alignMargin()}
            |
            """.trimMargin()
        )
        gradle("compileKotlin", expectFailure = true) {
            assertTrue(
                output.contains(expectedMessage),
                "expected diagnostic message <$expectedMessage> in build output, but was:\n$output"
            )
        }
    }

    private fun assertCompiles(
        @Language("kotlin") code: String,
    ) = context(project) {
        bootstrapKotlinJvmProject()
        file("src/main/kotlin/Subject.kt").kotlin(
            """
            |import dev.whyoleg.sweetspi.*
            |
            |${code.alignMargin()}
            |
            """.trimMargin()
        )
        gradle("compileKotlin") {
            assertTrue(task(":compileKotlin")!!.outcome.isPositive)
        }
    }

    @Test
    fun serviceOnFinalClass() = assertCompilationError(
        code = "@Service class NotAService",
        expectedMessage = "@Service is only applicable to interfaces or abstract classes"
    )

    @Test
    fun serviceWithPrivateVisibility() = assertCompilationError(
        code = "@Service private interface SecretService",
        expectedMessage = "@Service declaration must be public or internal"
    )
}

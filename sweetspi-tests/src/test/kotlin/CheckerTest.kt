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

    @Test
    fun providesAndServiceTogether() = assertCompilationError(
        code = """
        @Service interface S
        @Service @Provides(S::class) interface Both : S
        """.trimIndent(),
        expectedMessage = "both @Service and @Provides"
    )

    @Test
    fun providesServiceNotAnnotated() = assertCompilationError(
        code = """
        interface Plain
        @Provides(Plain::class) object Impl : Plain
        """.trimIndent(),
        expectedMessage = "is not annotated with @Service"
    )

    @Test
    fun providesTypeMismatch() = assertCompilationError(
        code = """
        @Service interface S
        class Unrelated
        @Provides(S::class) object Impl : Unrelated()
        """.trimIndent(),
        expectedMessage = "not a subtype of service"
    )

    @Test
    fun providesClassWrongVisibility() = assertCompilationError(
        code = """
        @Service interface S
        @Provides(S::class) private object Impl : S
        """.trimIndent(),
        expectedMessage = "@Provides declaration must be public or internal"
    )

    @Test
    fun providesAbstractClass() = assertCompilationError(
        code = """
        @Service interface S
        @Provides(S::class) abstract class Impl : S
        """.trimIndent(),
        expectedMessage = "must be an object or an instantiable class"
    )

    @Test
    fun providesInnerClass() = assertCompilationError(
        code = """
        @Service interface S
        class Outer {
            @Provides(S::class) inner class Impl : S
        }
        """.trimIndent(),
        expectedMessage = "@Provides class must not be inner"
    )

    @Test
    fun providesClassWithRequiredConstructorParam() = assertCompilationError(
        code = """
        @Service interface S
        @Provides(S::class) class Impl(val required: String) : S
        """.trimIndent(),
        expectedMessage = "must have a public or internal constructor"
    )
}

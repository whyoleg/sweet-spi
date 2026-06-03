/*
 * Copyright (c) 2026 Oleg Yukhnevich. Use of this source code is governed by the Apache 2.0 license.
 */

package dev.whyoleg.sweetspi.tests

import dev.whyoleg.sweetspi.tests.api.*
import org.intellij.lang.annotations.*
import kotlin.io.path.*
import kotlin.test.*

// Verifies @Service / @Provides FIR checkers fire on multiplatform COMMON compilation,
// not just on the JVM single-target path (covered by CheckerTest).
class CheckerKmpTest : TestProjectBasedTest() {

    private fun bootstrapCheckerKmpProject(@Language("kotlin") code: String) = context(project) {
        bootstrapKotlinMultiplatformProject()
        file("build.gradle.kts").appendText(
            """
            kotlin { jvm() }
            """.trimIndent()
        )
        file("src/commonMain/kotlin/Subject.kt").kotlin(
            """
            |import dev.whyoleg.sweetspi.*
            |
            |${code.alignMargin()}
            |
            """.trimMargin()
        )
    }

    private fun assertCompilationError(
        @Language("kotlin") code: String,
        expectedMessage: String,
    ) = context(project) {
        bootstrapCheckerKmpProject(code)
        gradle("compileKotlinJvm", expectFailure = true) {
            assertTrue(
                output.contains(expectedMessage),
                "expected diagnostic message <$expectedMessage> in build output, but was:\n$output"
            )
        }
    }

    private fun assertCompiles(
        @Language("kotlin") code: String,
    ) = context(project) {
        bootstrapCheckerKmpProject(code)
        gradle("compileKotlinJvm") {
            assertTrue(task(":compileKotlinJvm")!!.outcome.isPositive)
        }
    }

    @Test
    fun serviceOnFinalClass() = assertCompilationError(
        code = "@Service class NotAService",
        expectedMessage = "@Service is only applicable to interfaces or abstract classes"
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
    fun providesTypeMismatch() = assertCompilationError(
        code = """
        @Service interface S
        class Unrelated
        @Provides(S::class) object Impl : Unrelated()
        """.trimIndent(),
        expectedMessage = "not a subtype of service"
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
    fun providesFunctionInClassRejected() = assertCompilationError(
        code = """
        @Service interface S
        class Impl : S
        class Holder { @Provides(S::class) fun provide(): S = Impl() }
        """.trimIndent(),
        expectedMessage = "@Provides function/property must be top-level or a member of an object"
    )

    @Test
    fun validObjectProviderCompiles() = assertCompiles(
        code = """
        @Service interface S
        @Provides(S::class) object Impl : S
        """.trimIndent()
    )
}

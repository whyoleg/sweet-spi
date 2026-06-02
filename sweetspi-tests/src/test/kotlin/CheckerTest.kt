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

    @Test
    fun providesSuspendFunction() = assertCompilationError(
        code = """
        @Service interface S
        class Impl : S
        @Provides(S::class) suspend fun provide(): S = Impl()
        """.trimIndent(),
        expectedMessage = "@Provides function must not be suspend"
    )

    @Test
    fun providesFunctionWithReceiver() = assertCompilationError(
        code = """
        @Service interface S
        class Impl : S
        @Provides(S::class) fun String.provide(): S = Impl()
        """.trimIndent(),
        expectedMessage = "must not have an extension receiver or context parameters"
    )

    @Test
    fun providesFunctionWithRequiredParam() = assertCompilationError(
        code = """
        @Service interface S
        class Impl : S
        @Provides(S::class) fun provide(required: String): S = Impl()
        """.trimIndent(),
        expectedMessage = "@Provides function/property must not have an extension receiver or context parameters"
    )

    @Test
    fun providesFunctionReturnsNonService() = assertCompilationError(
        code = """
        @Service interface S
        class Unrelated
        @Provides(S::class) fun provide(): Unrelated = Unrelated()
        """.trimIndent(),
        expectedMessage = "not a subtype of service"
    )

    @Test
    fun providesPropertyWithReceiver() = assertCompilationError(
        code = """
        @Service interface S
        class Impl : S
        @Provides(S::class) val String.provided: S get() = Impl()
        """.trimIndent(),
        expectedMessage = "must not have an extension receiver or context parameters"
    )

    @Test
    fun providesPropertyOfNonService() = assertCompilationError(
        code = """
        @Service interface S
        class Unrelated
        @Provides(S::class) val provided: Unrelated = Unrelated()
        """.trimIndent(),
        expectedMessage = "not a subtype of service"
    )

    @Test
    fun validObjectProviderCompiles() = assertCompiles(
        code = """
        @Service interface S
        @Provides(S::class) object Impl : S
        """.trimIndent()
    )

    @Test
    fun validGenericServiceCompiles() = assertCompiles(
        code = """
        @Service abstract class S<T>(val value: T)
        @Provides(S::class) object SString : S<String>("x")
        """.trimIndent()
    )

    @Test
    fun validFunctionWithAllDefaultParamsCompiles() = assertCompiles(
        code = """
        @Service interface S
        class Impl : S
        @Provides(S::class) fun provide(tag: String = "default"): S = Impl()
        """.trimIndent()
    )

    @Test
    fun providesFunctionInObjectCompiles() = assertCompiles(
        code = """
        @Service interface S
        class Impl : S
        object Holder { @Provides(S::class) fun provide(): S = Impl() }
        """.trimIndent()
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
    fun providesPropertyInClassRejected() = assertCompilationError(
        code = """
        @Service interface S
        class Impl : S
        class Holder { @Provides(S::class) val provided: S = Impl() }
        """.trimIndent(),
        expectedMessage = "@Provides function/property must be top-level or a member of an object"
    )

    @Test
    fun providesDuplicateService() = assertCompilationError(
        code = """
        @Service interface S
        @Provides(S::class) @Provides(S::class) object Impl : S
        """.trimIndent(),
        expectedMessage = "is provided more than once"
    )
}

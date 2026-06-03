/*
 * Copyright (c) 2026 Oleg Yukhnevich. Use of this source code is governed by the Apache 2.0 license.
 */

package dev.whyoleg.sweetspi.tests

import dev.whyoleg.sweetspi.tests.api.*
import org.intellij.lang.annotations.*
import kotlin.test.*

// Verifies the @Service / @Provides FIR checkers fire identically on EVERY canonical KMP target
// (kmpCheckerScenario compiles the snippet for each target and asserts the diagnostic per call). Mirrors
// the plain-JVM cases in CheckerTest; unifying the two definition sets is deferred to M2 (JVM-test DSL).
class CheckerKmpTest : TestProjectBasedTest() {

    private fun failsWith(@Language("kotlin") code: String, message: String) = context(project) {
        kmpCheckerScenario(code) { failsWith(message) }
    }

    private fun compiles(@Language("kotlin") code: String) = context(project) {
        kmpCheckerScenario(code) { compiles() }
    }

    @Test
    fun serviceOnFinalClass() = failsWith(
        "@Service class NotAService",
        "@Service is only applicable to interfaces or abstract classes"
    )

    @Test
    fun serviceWithPrivateVisibility() = failsWith(
        "@Service private interface SecretService",
        "@Service declaration must be public or internal"
    )

    @Test
    fun providesAndServiceTogether() = failsWith(
        """
        @Service interface S
        @Service @Provides(S::class) interface Both : S
        """.trimIndent(),
        "both @Service and @Provides"
    )

    @Test
    fun providesServiceNotAnnotated() = failsWith(
        """
        interface Plain
        @Provides(Plain::class) object Impl : Plain
        """.trimIndent(),
        "is not annotated with @Service"
    )

    @Test
    fun providesTypeMismatch() = failsWith(
        """
        @Service interface S
        class Unrelated
        @Provides(S::class) object Impl : Unrelated()
        """.trimIndent(),
        "not a subtype of service"
    )

    @Test
    fun providesClassWrongVisibility() = failsWith(
        """
        @Service interface S
        @Provides(S::class) private object Impl : S
        """.trimIndent(),
        "@Provides declaration must be public or internal"
    )

    @Test
    fun providesAbstractClass() = failsWith(
        """
        @Service interface S
        @Provides(S::class) abstract class Impl : S
        """.trimIndent(),
        "must be an object or an instantiable class"
    )

    @Test
    fun providesInnerClass() = failsWith(
        """
        @Service interface S
        class Outer {
            @Provides(S::class) inner class Impl : S
        }
        """.trimIndent(),
        "@Provides class must not be inner"
    )

    @Test
    fun providesClassWithRequiredConstructorParam() = failsWith(
        """
        @Service interface S
        @Provides(S::class) class Impl(val required: String) : S
        """.trimIndent(),
        "must have a public or internal constructor"
    )

    @Test
    fun providesSuspendFunction() = failsWith(
        """
        @Service interface S
        class Impl : S
        @Provides(S::class) suspend fun provide(): S = Impl()
        """.trimIndent(),
        "@Provides function must not be suspend"
    )

    @Test
    fun providesFunctionWithReceiver() = failsWith(
        """
        @Service interface S
        class Impl : S
        @Provides(S::class) fun String.provide(): S = Impl()
        """.trimIndent(),
        "must not have an extension receiver or context parameters"
    )

    @Test
    fun providesFunctionWithRequiredParam() = failsWith(
        """
        @Service interface S
        class Impl : S
        @Provides(S::class) fun provide(required: String): S = Impl()
        """.trimIndent(),
        "@Provides function/property must not have an extension receiver or context parameters"
    )

    @Test
    fun providesFunctionReturnsNonService() = failsWith(
        """
        @Service interface S
        class Unrelated
        @Provides(S::class) fun provide(): Unrelated = Unrelated()
        """.trimIndent(),
        "not a subtype of service"
    )

    @Test
    fun providesPropertyWithReceiver() = failsWith(
        """
        @Service interface S
        class Impl : S
        @Provides(S::class) val String.provided: S get() = Impl()
        """.trimIndent(),
        "must not have an extension receiver or context parameters"
    )

    @Test
    fun providesPropertyOfNonService() = failsWith(
        """
        @Service interface S
        class Unrelated
        @Provides(S::class) val provided: Unrelated = Unrelated()
        """.trimIndent(),
        "not a subtype of service"
    )

    @Test
    fun providesFunctionInClassRejected() = failsWith(
        """
        @Service interface S
        class Impl : S
        class Holder { @Provides(S::class) fun provide(): S = Impl() }
        """.trimIndent(),
        "@Provides function/property must be top-level or a member of an object"
    )

    @Test
    fun providesPropertyInClassRejected() = failsWith(
        """
        @Service interface S
        class Impl : S
        class Holder { @Provides(S::class) val provided: S = Impl() }
        """.trimIndent(),
        "@Provides function/property must be top-level or a member of an object"
    )

    @Test
    fun providesDuplicateService() = failsWith(
        """
        @Service interface S
        @Provides(S::class) @Provides(S::class) object Impl : S
        """.trimIndent(),
        "is provided more than once"
    )

    @Test
    fun validObjectProviderCompiles() = compiles(
        """
        @Service interface S
        @Provides(S::class) object Impl : S
        """.trimIndent()
    )

    @Test
    fun validGenericServiceCompiles() = compiles(
        """
        @Service abstract class S<T>(val value: T)
        @Provides(S::class) object SString : S<String>("x")
        """.trimIndent()
    )

    @Test
    fun validFunctionWithAllDefaultParamsCompiles() = compiles(
        """
        @Service interface S
        class Impl : S
        @Provides(S::class) fun provide(tag: String = "default"): S = Impl()
        """.trimIndent()
    )

    @Test
    fun providesFunctionInObjectCompiles() = compiles(
        """
        @Service interface S
        class Impl : S
        object Holder { @Provides(S::class) fun provide(): S = Impl() }
        """.trimIndent()
    )
}

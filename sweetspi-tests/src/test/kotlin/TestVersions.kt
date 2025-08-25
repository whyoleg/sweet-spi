/*
 * Copyright (c) 2024-2025 Oleg Yukhnevich. Use of this source code is governed by the Apache 2.0 license.
 */

package dev.whyoleg.sweetspi.tests

import org.junit.jupiter.api.extension.*
import org.junit.jupiter.params.*
import org.junit.jupiter.params.provider.*
import java.util.stream.*
import kotlin.streams.*

data class TestVersions(
    val gradleVersion: String,
    val kotlinVersion: String,
    val kspVersion: String,
)

private const val MIN_GRADLE = "8.0.2"
private const val LATEST_GRADLE = "8.14.3"

abstract class TestVersionsProvider(private val versions: Sequence<TestVersions>) : ArgumentsProvider {
    constructor(block: suspend SequenceScope<TestVersions>.() -> Unit) : this(sequence(block))

    override fun provideArguments(context: ExtensionContext?): Stream<out Arguments> = versions.map { Arguments.of(it) }.asStream()

    class LatestGradle : TestVersionsProvider({
        yield(
            TestVersions(
                gradleVersion = LATEST_GRADLE,
                kotlinVersion = TestsArguments.kspVersion.substringBeforeLast("-"),
                kspVersion = TestsArguments.kspVersion
            )
        )
    })

    class All : TestVersionsProvider({
        listOf(MIN_GRADLE, LATEST_GRADLE).forEach { gradleVersion ->
            yield(
                TestVersions(
                    gradleVersion = gradleVersion,
                    kotlinVersion = TestsArguments.kspVersion.substringBeforeLast("-"),
                    kspVersion = TestsArguments.kspVersion
                )
            )
        }
    })
}

@ParameterizedTest
@ArgumentsSource(TestVersionsProvider.LatestGradle::class)
annotation class FastVersionedTest

@ParameterizedTest
@ArgumentsSource(TestVersionsProvider.All::class)
annotation class FullVersionedTest

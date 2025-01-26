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
private const val LATEST_GRADLE = "8.12"
private val KSP_VERSIONS = listOf(
    "2.0.0-1.0.24",
    "2.0.10-1.0.24",
    "2.0.21-1.0.28",
    "2.1.0-1.0.29",
    "2.1.10-RC-1.0.29",
    "2.1.20-Beta1-1.0.29",
)

abstract class TestVersionsProvider(private val versions: Sequence<TestVersions>) : ArgumentsProvider {
    constructor(block: suspend SequenceScope<TestVersions>.() -> Unit) : this(sequence(block))

    override fun provideArguments(context: ExtensionContext?): Stream<out Arguments> = versions.map { Arguments.of(it) }.asStream()

    class LatestGradle : TestVersionsProvider({
        KSP_VERSIONS.forEach { kspVersion ->
            yield(
                TestVersions(
                    gradleVersion = LATEST_GRADLE,
                    kotlinVersion = kspVersion.substringBeforeLast("-"),
                    kspVersion = kspVersion
                )
            )
        }
    })

    class All : TestVersionsProvider({
        listOf(MIN_GRADLE, LATEST_GRADLE).forEach { gradleVersion ->
            KSP_VERSIONS.forEach { kspVersion ->
                yield(
                    TestVersions(
                        gradleVersion = gradleVersion,
                        kotlinVersion = kspVersion.substringBeforeLast("-"),
                        kspVersion = kspVersion
                    )
                )
            }
        }
    })
}

@ParameterizedTest
@ArgumentsSource(TestVersionsProvider.LatestGradle::class)
annotation class FastVersionedTest

@ParameterizedTest
@ArgumentsSource(TestVersionsProvider.All::class)
annotation class FullVersionedTest

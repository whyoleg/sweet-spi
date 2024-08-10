/*
 * Copyright (c) 2024 Oleg Yukhnevich. Use of this source code is governed by the Apache 2.0 license.
 */

package dev.whyoleg.sweetspi.tests

import org.junit.jupiter.api.extension.*
import org.junit.jupiter.params.provider.*
import java.util.stream.*
import kotlin.streams.*

abstract class TestVersionsProvider(private val versions: Sequence<TestVersions>) : ArgumentsProvider {
    constructor(block: suspend SequenceScope<TestVersions>.() -> Unit) : this(sequence(block))

    override fun provideArguments(context: ExtensionContext?): Stream<out Arguments> = versions.map { Arguments.of(it) }.asStream()

    object All : TestVersionsProvider({
        listOf(
            "8.0.2",    // oldest supported
            "8.9",      // latest stable
            "8.10-rc-1" // latest RC
        ).forEach { gradleVersion ->
            listOf(
                "2.0.0-1.0.24",    // oldest supported
                "2.0.10-1.0.24",   // latest stable
                "2.0.20-RC-1.0.24" // latest RC
            ).forEach { kspVersion ->
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

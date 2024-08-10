/*
 * Copyright (c) 2024 Oleg Yukhnevich. Use of this source code is governed by the Apache 2.0 license.
 */

package dev.whyoleg.sweetspi.tests

import org.gradle.testkit.runner.*
import java.nio.file.*

class TestProject(
    private val projectDirectory: Path,
    private val versions: TestVersions,
) {
    fun gradle(vararg arguments: String, expectFailure: Boolean = false, block: BuildResult.() -> Unit) {
        val runner = GradleRunner.create()
            .withGradleVersion(versions.gradleVersion)
            .withProjectDir(projectDirectory.toFile())
            .forwardOutput()
            .withArguments("--stacktrace", *arguments)

        val result = when {
            expectFailure -> runner.buildAndFail()
            else          -> runner.build()
        }

        result.block()
    }
}

val TaskOutcome.isPositive get() = this in setOf(TaskOutcome.SUCCESS, TaskOutcome.FROM_CACHE)

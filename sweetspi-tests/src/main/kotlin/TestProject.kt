/*
 * Copyright (c) 2024 Oleg Yukhnevich. Use of this source code is governed by the Apache 2.0 license.
 */

package dev.whyoleg.sweetspi.tests

import org.gradle.testkit.runner.*
import java.nio.file.*

class TestProject(
    val projectDirectory: Path,
    private val versions: TestVersions,
) {
    fun gradleRunner(
        vararg arguments: String,
        withCache: Boolean = true,
    ): GradleRunner = GradleRunner.create()
        .withGradleVersion(versions.gradleVersion)
        .withProjectDir(projectDirectory.toFile())
        .forwardOutput()
        .withArguments(
            if (withCache) "--build-cache" else "--no-build-cache",
            "--stacktrace",
            *arguments,
        )
}

/*
 * Copyright (c) 2024 Oleg Yukhnevich. Use of this source code is governed by the Apache 2.0 license.
 */

package dev.whyoleg.sweetspi.tests

import org.junit.jupiter.api.*
import java.nio.file.*
import kotlin.io.path.*

abstract class AbstractTest : TestProjectFactory {
    private lateinit var temporaryDirectory: Path

    @BeforeEach
    fun setupTestInfo(testInfo: TestInfo) {
        temporaryDirectory = if (System.getenv("CI") != null) {
            Files.createTempDirectory("tests")
        } else {
            Path("build/test-projects")
                .resolve(testInfo.testClass.get().name.substringAfter("dev.whyoleg.sweetspi.tests.").replace(".", "/"))
                .resolve(testInfo.testMethod.get().name)
                .also(Path::deleteRecursively)
        }
    }

    final override val projectDirectory: Path
        get() = temporaryDirectory.resolve("project").createDirectories()
}

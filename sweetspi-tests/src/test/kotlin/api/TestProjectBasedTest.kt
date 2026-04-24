/*
 * Copyright (c) 2026 Oleg Yukhnevich. Use of this source code is governed by the Apache 2.0 license.
 */

package dev.whyoleg.sweetspi.tests.api

import org.junit.jupiter.api.*
import java.nio.file.*
import kotlin.io.path.*

abstract class TestProjectBasedTest {
    lateinit var project: TestProject

    @BeforeEach
    fun setupTestInfo(testInfo: TestInfo) {
        project = TestProject(
            name = testInfo.testMethod.get().name,
            projectDirectory = Path("build/test-projects")
                .resolve(testInfo.testClass.get().name)
                .resolve(testInfo.testMethod.get().name)
// TODO: it's faster without delete, but if the test layout changes, it may lead to unexpected behavior
//                .also(Path::deleteRecursively)
                .createDirectories()
        )
    }
}

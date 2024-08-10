/*
 * Copyright (c) 2024 Oleg Yukhnevich. Use of this source code is governed by the Apache 2.0 license.
 */

package dev.whyoleg.sweetspi.tests

import java.nio.file.*

interface TestProjectFactory {
    val projectDirectory: Path

    val defaultTemplate: TestTemplate? get() = null
    val defaultVersions: TestVersions? get() = null

    fun project(
        template: TestTemplate = defaultTemplate ?: error("No default 'template' for $this"),
        versions: TestVersions = defaultVersions ?: error("No default 'versions' for $this"),
        block: TestProjectBuilder.() -> Unit,
    ): TestProject = TestProjectBuilder(template, versions, projectDirectory).apply(block).build()
}

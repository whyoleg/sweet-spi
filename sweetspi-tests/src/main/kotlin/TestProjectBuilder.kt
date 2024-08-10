/*
 * Copyright (c) 2024 Oleg Yukhnevich. Use of this source code is governed by the Apache 2.0 license.
 */

package dev.whyoleg.sweetspi.tests

import java.nio.file.*
import kotlin.io.path.*

class TestProjectBuilder(
    template: TestTemplate,
    private val versions: TestVersions,
    val projectDirectory: Path,
) {
    init {
        template.templatePath.copyToRecursively(projectDirectory, followLinks = false, overwrite = true)
        projectDirectory.resolve("settings.gradle.kts").rewriteSettingsGradleKts()
        projectDirectory.resolve("gradle/libs.versions.toml").rewriteLibsVersionsToml(versions)
    }

    fun rewrite(relativePath: String, block: (String) -> String) {
        projectDirectory.resolve(relativePath).rewrite(block)
    }

    fun append(relativePath: String, block: () -> String) {
        rewrite(relativePath) { it + "\n" + block() }
    }

    fun prepend(relativePath: String, block: () -> String) {
        rewrite(relativePath) { block() + "\n" + it }
    }

    fun file(relativePath: String, block: () -> String) {
        projectDirectory.resolve(relativePath).createParentDirectories().writeText(block())
    }

    fun build(): TestProject = TestProject(projectDirectory, versions)
}

private fun Path.rewriteSettingsGradleKts(): Unit = rewrite {
    it.replace(
        oldValue = "%DEV_ARTIFACTS_REPOSITORIES",
        """
        |exclusiveContent {
        |    filter { includeGroup("dev.whyoleg.sweetspi") }
        |    forRepositories(
        |        ${TestsArguments.devArtifactsDirectories.joinToString(",\n|        ") { "maven(\"$it\")" }}
        |    )
        |}
        """.trimMargin()
    )
}

private fun Path.rewriteLibsVersionsToml(versions: TestVersions): Unit = rewrite {
    it.replace(
        oldValue = "%KOTLIN_VERSION",
        newValue = versions.kotlinVersion
    ).replace(
        oldValue = "%KSP_VERSION",
        newValue = versions.kspVersion
    ).replace(
        oldValue = "%SWEETSPI_VERSION",
        newValue = TestsArguments.devArtifactsVersion
    )
}

private fun Path.rewrite(block: (String) -> String): Unit = writeText(block(readText()))

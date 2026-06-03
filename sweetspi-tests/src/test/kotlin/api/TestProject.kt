/*
 * Copyright (c) 2024-2026 Oleg Yukhnevich. Use of this source code is governed by the Apache 2.0 license.
 */

package dev.whyoleg.sweetspi.tests.api

import org.gradle.testkit.runner.*
import org.intellij.lang.annotations.*
import java.nio.file.*
import kotlin.io.path.*

class TestProject(
    val name: String,
    val projectDirectory: Path,
)

val TaskOutcome.isPositive get() = this in setOf(TaskOutcome.SUCCESS, TaskOutcome.FROM_CACHE, TaskOutcome.UP_TO_DATE)

context(project: TestProject)
fun gradle(
    vararg arguments: String,
    expectFailure: Boolean = false,
    forwardOutput: Boolean = true,
    rerunTasks: Boolean = false, // useful for debugging tests
    block: BuildResult.() -> Unit = {},
) {
    val runner = GradleRunner.create().apply {
        withProjectDir(project.projectDirectory.toFile())
        if (forwardOutput) {
            forwardOutput()
        }
        withArguments(buildList {
            add("--stacktrace")
            addAll(arguments)
            if (rerunTasks) add("--rerun-tasks")
        })
    }
    val result = when {
        expectFailure -> runner.buildAndFail()
        else          -> runner.build()
    }

    result.block()
}

context(project: TestProject)
fun bootstrapKotlinJvmProject() {
    bootstrapGradleProject()
    jvmBuildFile()
}

context(project: TestProject)
fun bootstrapKotlinMultiplatformProject() {
    bootstrapGradleProject()
    multiplatformBuildFile()
}

// Single-module multiplatform project on the canonical execution matrix + shared hierarchy
// (see multiplatformExecutionBuildFile). Used by all single-module execution tests.
context(project: TestProject)
fun bootstrapKotlinMultiplatformExecutionProject() {
    bootstrapGradleProject()
    multiplatformExecutionBuildFile()
}

// Multi-module project: writes shared settings (with `include(...)`), properties and version catalog.
// The caller writes each module's build file (jvmBuildFile/multiplatformBuildFile) + sources via file(...).
// No root build.gradle.kts is written here.
context(project: TestProject)
fun bootstrapMultiModuleProject(modules: List<String>) {
    bootstrapGradleProject(includes = modules)
}

context(project: TestProject)
private fun bootstrapGradleProject(includes: List<String> = emptyList()) {
    file("gradle.properties").writeText(
        """
        org.gradle.parallel=true
        org.gradle.caching=true
        org.gradle.configuration-cache=true
        org.gradle.configuration-cache.parallel=true
        org.gradle.jvmargs=-Xmx2g
        
        """.trimIndent()
    )
    file("gradle/libs.versions.toml").writeText(
        """
        [versions]
        kotlin = "${TestsArguments.kotlinVersion}"
        sweetspi = "${TestsArguments.devArtifactsVersion}"
        
        [plugins]
        kotlin-jvm = { id = "org.jetbrains.kotlin.jvm", version.ref = "kotlin" }
        kotlin-multiplatform = { id = "org.jetbrains.kotlin.multiplatform", version.ref = "kotlin" }
        sweetspi = { id = "dev.whyoleg.sweetspi", version.ref = "sweetspi" }
        
        """.trimIndent()
    )

    val devArtifactsRepositories =
        """
        |exclusiveContent {
        |    filter { includeGroup("dev.whyoleg.sweetspi") }
        |    forRepositories(
        |        ${TestsArguments.devArtifactsDirectories.joinToString(",\n|        ") { "maven(\"$it\")" }}
        |    )
        |}
        """.trimMargin().alignMargin("|        ") // to be aligned in `settings.gradle.kts`

    val includeStatements = includes.joinToString("\n") { "include(\"$it\")" }

    file("settings.gradle.kts").kts(
        buildString {
            append(
                """
                |pluginManagement {
                |    repositories {
                |        $devArtifactsRepositories
                |        mavenCentral()
                |        gradlePluginPortal()
                |    }
                |}
                |
                |dependencyResolutionManagement {
                |    repositories {
                |        $devArtifactsRepositories
                |        mavenCentral()
                |    }
                |}
                |
                """.trimMargin()
            )
            if (includeStatements.isNotEmpty()) append(includeStatements).append("\n")
        }
    )
}

context(project: TestProject)
fun file(path: String): Path = project.projectDirectory.resolve(path).createParentDirectories()

context(project: TestProject)
fun Path.kotlin(
    @Language("kotlin") code: String,
): Unit = kotlin("sweettests.${project.name}", code)

context(project: TestProject)
fun Path.kotlin(
    packageName: String,
    @Language("kotlin") code: String,
): Unit = writeText("package $packageName\n\n$code")

context(project: TestProject)
fun Path.kts(
    @Language("kotlin") code: String,
): Unit = writeText(code)

fun String.alignMargin(prefix: String = "|"): String = lines().joinToString("\n$prefix")

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

val TaskOutcome.isPositive get() = this in setOf(TaskOutcome.SUCCESS, TaskOutcome.FROM_CACHE)

context(project: TestProject)
fun gradle(vararg arguments: String, expectFailure: Boolean = false, block: BuildResult.() -> Unit) {
    val runner = GradleRunner.create()
        .withProjectDir(project.projectDirectory.toFile())
        .forwardOutput()
        .withArguments("--stacktrace", *arguments)

    val result = when {
        expectFailure -> runner.buildAndFail()
        else          -> runner.build()
    }

    result.block()
}

context(project: TestProject)
fun bootstrapKotlinJvmProject() {
    bootstrapGradleProject()

    file("build.gradle.kts").kts(
        """
        import org.jetbrains.kotlin.gradle.dsl.abi.ExperimentalAbiValidation
        
        plugins {
            alias(libs.plugins.kotlin.jvm)
            alias(libs.plugins.sweetspi)
        }
        @OptIn(ExperimentalAbiValidation::class)
        kotlin {
            abiValidation { enabled = true }
        }
        dependencies { 
            implementation(kotlin("test"))
        }
        """.trimIndent()
    )
}

context(project: TestProject)
fun bootstrapKotlinMultiplatformProject() {
    bootstrapGradleProject()

    file("build.gradle.kts").kts(
        """
        plugins {
            alias(libs.plugins.kotlin.multiplatform)
            alias(libs.plugins.sweetspi)
        }
        kotlin { 
            sourceSets.commonTest.dependencies { 
                implementation(kotlin("test"))
            }
        }
        """.trimIndent()
    )
}

context(project: TestProject)
private fun bootstrapGradleProject() {
    val devArtifactsRepositories =
        """
        exclusiveContent {
            filter { includeGroup("dev.whyoleg.sweetspi") }
            forRepositories(
                ${TestsArguments.devArtifactsDirectories.joinToString(",\n        ") { "maven(\"$it\")" }}
            )
        }
        """.trimIndent()
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
    file("settings.gradle.kts").kts(
        """
        pluginManagement {
            repositories {
                $devArtifactsRepositories
                mavenCentral()
                gradlePluginPortal()
            }
        }

        dependencyResolutionManagement {
            repositories {
                $devArtifactsRepositories
                mavenCentral()
            }
        }
        """.trimIndent()
    )
    file("gradle.properties").writeText(
        """
        org.gradle.parallel=true
        org.gradle.caching=true
        org.gradle.configuration-cache=true
        org.gradle.configuration-cache.parallel=true
        org.gradle.jvmargs=-Xmx2g
        """.trimIndent()
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

/*
 * Copyright (c) 2026 Oleg Yukhnevich. Use of this source code is governed by the Apache 2.0 license.
 */

package dev.whyoleg.sweetspi.tests.api

import java.nio.file.*

private const val ABI_IMPORT = "import org.jetbrains.kotlin.gradle.dsl.abi.ExperimentalAbiValidation"

// Writes <path>/build.gradle.kts for a kotlin-jvm + sweetspi module. path="" = root module.
// ABI validation is enabled and kotlin("test") is always added. Returns the written file's Path so the
// caller can chain `.appendText(...)`, e.g. .appendText("dependencies { api(project(\":service\")) }").
context(project: TestProject)
fun jvmBuildFile(path: String = ""): Path = writeBuildFile(path, buildString {
    appendLine(ABI_IMPORT).appendLine()
    appendLine("plugins {")
    appendLine("    alias(libs.plugins.kotlin.jvm)")
    appendLine("    alias(libs.plugins.sweetspi)")
    appendLine("}")
    appendLine("@OptIn(ExperimentalAbiValidation::class)")
    appendLine("kotlin { abiValidation { enabled = true } }")
    appendLine("dependencies {")
    appendLine("    implementation(kotlin(\"test\"))")
    appendLine("}")
})

// Writes <path>/build.gradle.kts for a kotlin-multiplatform + sweetspi module. path="" = root module.
// `targets` are target names rendered as calls, e.g. listOf("jvm", "macosArm64") -> jvm(); macosArm64().
// ABI validation is enabled and kotlin("test") is always added to commonTest. Returns the written
// file's Path so the caller can chain `.appendText(...)`, e.g.
// .appendText("kotlin { sourceSets.commonMain.dependencies { api(project(\":service\")) } }").
context(project: TestProject)
fun multiplatformBuildFile(
    path: String = "",
    targets: List<String> = emptyList(),
): Path = writeBuildFile(path, buildString {
    appendLine(ABI_IMPORT).appendLine()
    appendLine("plugins {")
    appendLine("    alias(libs.plugins.kotlin.multiplatform)")
    appendLine("    alias(libs.plugins.sweetspi)")
    appendLine("}")
    appendLine("@OptIn(ExperimentalAbiValidation::class)")
    appendLine("kotlin {")
    appendLine("    abiValidation { enabled = true }")
    targets.forEach { appendLine("    $it()") }
    appendLine("    sourceSets.commonTest.dependencies {")
    appendLine("        implementation(kotlin(\"test\"))")
    appendLine("    }")
    appendLine("}")
})

context(project: TestProject)
private fun writeBuildFile(path: String, content: String): Path {
    val filePath = if (path.isEmpty()) "build.gradle.kts" else "$path/build.gradle.kts"
    return file(filePath).also { it.kts(content) }
}

/*
 * Copyright (c) 2026 Oleg Yukhnevich. Use of this source code is governed by the Apache 2.0 license.
 */

package dev.whyoleg.sweetspi.tests.api

import java.nio.file.*
import kotlin.io.path.*

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

// ---------------------------------------------------------------------------------------------------
// Canonical multiplatform execution setup — the single target matrix + source-set hierarchy used by
// all execution tests (provider forms, source-set placement, cross-module).
//
// Targets: jvm, macosArm64 (run), linuxX64 (compile-only on a macOS host — its test task is skipped),
// wasmJs + wasmWasi (run). `js` is intentionally absent (wasmJs covers the web family; cross-module js
// is a known limitation) — tests that need it add `kotlin { js { nodejs() } }` explicitly.
//
// Shared intermediate source sets, always created via the default hierarchy template extension:
//   - nonJvm        : native + js + wasmJs + wasmWasi  (everything except jvm)
//   - jvmAndNative  : jvm + native                     (the registry/META-INF divergence case)
// The template creates the matching `*Test` source sets automatically.
// ---------------------------------------------------------------------------------------------------

private val MP_EXECUTION_TARGETS = """
    |@OptIn(org.jetbrains.kotlin.gradle.ExperimentalWasmDsl::class, org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi::class)
    |kotlin {
    |    jvm()
    |    macosArm64()
    |    linuxX64()
    |    wasmJs { nodejs() }
    |    wasmWasi { nodejs() }
    |    applyDefaultHierarchyTemplate {
    |        common {
    |            group("nonJvm") {
    |                withNative()
    |                withJs()
    |                withWasmJs()
    |                withWasmWasi()
    |            }
    |            group("jvmAndNative") {
    |                withJvm()
    |                withNative()
    |            }
    |        }
    |    }
    |}
    |
""".trimMargin()

// Writes <path>/build.gradle.kts for a kotlin-multiplatform + sweetspi module using the canonical
// execution matrix + hierarchy above. Returns the Path so the caller can chain dependency config, e.g.
// .appendText("kotlin { sourceSets.commonMain.dependencies { api(project(\":service\")) } }").
context(project: TestProject)
fun multiplatformExecutionBuildFile(path: String = ""): Path =
    multiplatformBuildFile(path).also { it.appendText(MP_EXECUTION_TARGETS) }

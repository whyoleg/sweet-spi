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

// ---------------------------------------------------------------------------------------------------
// Multiplatform hierarchy helpers — for tests that exercise FIR generation across source-set DAGs.
// Usage: bootstrapKotlinMultiplatformProject() then
//   file("build.gradle.kts").appendText(multiplatformTargets(extra = nonJvmSourceSets()))
// The returned text is a standalone `kotlin { }` block (KGP merges multiple `kotlin {}` blocks).
// On a macOS host only macos*/ios-simulator native tests execute; linuxX64 compiles+links but its
// test task is skipped — so use it for per-target compile coverage, assert only host-runnable tests.
// ---------------------------------------------------------------------------------------------------

fun multiplatformTargets(
    jvm: Boolean = true,
    natives: List<String> = listOf("macosArm64", "linuxX64"),
    js: Boolean = true,
    wasmJs: Boolean = true,
    extra: String = "",
): String = buildString {
    appendLine("kotlin {")
    if (jvm) appendLine("    jvm()")
    natives.forEach { appendLine("    $it()") }
    if (js) appendLine("    js { nodejs() }")
    if (wasmJs) appendLine("    wasmJs { nodejs() }")
    if (extra.isNotEmpty()) appendLine(extra.prependIndent("    "))
    appendLine("}")
}

// Inserts a custom `nonJvm` intermediate between commonMain and the native/js/wasmJs groups
// (main + test). A `@Service` here is never seen by the jvm compilation. Requires native + js + wasmJs
// targets to be declared.
fun nonJvmSourceSets(): String = """
    |applyDefaultHierarchyTemplate()
    |sourceSets {
    |    val nonJvm by creating { dependsOn(commonMain.get()) }
    |    nativeMain.get().dependsOn(nonJvm)
    |    jsMain.get().dependsOn(nonJvm)
    |    wasmJsMain.get().dependsOn(nonJvm)
    |    val nonJvmTest by creating { dependsOn(commonTest.get()) }
    |    nativeTest.get().dependsOn(nonJvmTest)
    |    jsTest.get().dependsOn(nonJvmTest)
    |    wasmJsTest.get().dependsOn(nonJvmTest)
    |}
""".trimMargin()

// Inserts a custom `jvmAndNative` intermediate shared by jvm + native (main + test). A `@Service` here
// is split across the registry platform (native) and the META-INF platform (jvm) — the divergence case.
// Requires jvm + native targets to be declared.
fun jvmAndNativeSourceSets(): String = """
    |applyDefaultHierarchyTemplate()
    |sourceSets {
    |    val jvmAndNative by creating { dependsOn(commonMain.get()) }
    |    jvmMain.get().dependsOn(jvmAndNative)
    |    nativeMain.get().dependsOn(jvmAndNative)
    |    val jvmAndNativeTest by creating { dependsOn(commonTest.get()) }
    |    jvmTest.get().dependsOn(jvmAndNativeTest)
    |    nativeTest.get().dependsOn(jvmAndNativeTest)
    |}
""".trimMargin()

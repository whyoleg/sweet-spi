/*
 * Copyright (c) 2026 Oleg Yukhnevich. Use of this source code is governed by the Apache 2.0 license.
 */

package dev.whyoleg.sweetspi.tests.api

import org.gradle.testkit.runner.*
import org.intellij.lang.annotations.*
import kotlin.io.path.*
import kotlin.test.*

// ---------------------------------------------------------------------------------------------------
// KMP scenario DSL — express a test as "place code in modules/source sets, build on the canonical
// matrix, assert per target". Two runners: kmpCheckerScenario (frontend diagnostics, per-target
// compile) and kmpExecutionScenario (runtime discovery, per-target test). JVM-only testing is a
// separate concern (see roadmap M2). All code is passed inline by the calling test.
// ---------------------------------------------------------------------------------------------------

// The canonical KMP targets. `testTask == null` means the test cannot run on a macOS host (linuxX64) and
// is exercised compile-only via `testCompileTask`.
internal enum class KmpTarget(val targetName: String, val testTask: String?) {
    Jvm("jvm", "jvmTest"),
    MacosArm64("macosArm64", "macosArm64Test"),
    LinuxX64("linuxX64", null),
    WasmJs("wasmJs", "wasmJsNodeTest"),
    WasmWasi("wasmWasi", "wasmWasiNodeTest");

    val compileTask: String get() = "compileKotlin${targetName.replaceFirstChar(Char::uppercase)}"
    val testCompileTask: String get() = "compileTestKotlin${targetName.replaceFirstChar(Char::uppercase)}"
}

private val ALL_TARGETS = KmpTarget.entries.toSet()
private val NON_JVM = setOf(KmpTarget.MacosArm64, KmpTarget.LinuxX64, KmpTarget.WasmJs, KmpTarget.WasmWasi)
private val JVM_AND_NATIVE = setOf(KmpTarget.Jvm, KmpTarget.MacosArm64, KmpTarget.LinuxX64)
private val JVM_ONLY = setOf(KmpTarget.Jvm)

// ----------------------------------- checker scenario -----------------------------------

class KmpCheckerScope internal constructor() {
    internal var expectedDiagnostic: String? = null

    // The compilation must fail on every target with [message] present in the output.
    fun failsWith(message: String) {
        expectedDiagnostic = message
    }

    // The compilation must succeed on every target.
    fun compiles() {
        expectedDiagnostic = null
    }
}

// Places [code] in commonMain and compiles it for every canonical target, asserting the checker outcome
// per target. `import dev.whyoleg.sweetspi.*` is added automatically.
context(project: TestProject)
fun kmpCheckerScenario(@Language("kotlin") code: String, block: KmpCheckerScope.() -> Unit) {
    bootstrapKotlinMultiplatformExecutionProject()
    file("src/commonMain/kotlin/Subject.kt").kotlin("sweettests", "import dev.whyoleg.sweetspi.*\n\n$code")

    val scope = KmpCheckerScope().apply(block)
    val compileTasks = ALL_TARGETS.map { ":${it.compileTask}" }.toTypedArray()
    val diagnostic = scope.expectedDiagnostic

    if (diagnostic == null) {
        gradle(*compileTasks, forwardOutput = false) {
            ALL_TARGETS.forEach { t ->
                assertTrue(task(":${t.compileTask}")!!.outcome.isPositive, "${t.targetName} should compile")
            }
        }
    } else {
        gradle("--continue", *compileTasks, expectFailure = true, forwardOutput = false) {
            ALL_TARGETS.forEach { t ->
                assertEquals(
                    TaskOutcome.FAILED, task(":${t.compileTask}")?.outcome,
                    "${t.targetName} should fail compilation"
                )
            }
            assertTrue(output.contains(diagnostic), "expected diagnostic <$diagnostic> in output, but was:\n$output")
        }
    }
}

// ----------------------------------- execution scenario -----------------------------------

class KmpModuleScope internal constructor(internal val name: String) {
    internal val files = mutableListOf<SourceFile>()
    internal var testTargets: Set<KmpTarget>? = null
    private var counter = 0

    // main source sets — raw declarations (with `import dev.whyoleg.sweetspi.*` added)
    fun commonMain(@Language("kotlin") code: String) = main("commonMain", code)
    fun nonJvmMain(@Language("kotlin") code: String) = main("nonJvmMain", code)
    fun jvmAndNativeMain(@Language("kotlin") code: String) = main("jvmAndNativeMain", code)

    // test source sets — [body] is wrapped in a test class+method; sets which targets run the test
    fun commonTest(@Language("kotlin") body: String) = test("commonTest", ALL_TARGETS, body)
    fun nonJvmTest(@Language("kotlin") body: String) = test("nonJvmTest", NON_JVM, body)
    fun jvmAndNativeTest(@Language("kotlin") body: String) = test("jvmAndNativeTest", JVM_AND_NATIVE, body)

    // jvm-only test source set — runs on the Jvm target only (e.g. plain java.util.ServiceLoader checks)
    fun jvmTest(@Language("kotlin") body: String) = test("jvmTest", JVM_ONLY, body)

    private fun main(sourceSet: String, code: String) {
        files += SourceFile(sourceSet, "Source${counter++}.kt", "import dev.whyoleg.sweetspi.*\n\n$code")
    }

    private fun test(sourceSet: String, targets: Set<KmpTarget>, body: String) {
        check(testTargets == null) { "only one test source set per module is supported" }
        testTargets = targets
        files += SourceFile(
            sourceSet, "GeneratedTest.kt",
            """
            |import dev.whyoleg.sweetspi.*
            |import kotlin.test.*
            |
            |class GeneratedTest {
            |    @Test
            |    fun test() {
            |        ${body.alignMargin("|        ")}
            |    }
            |}
            """.trimMargin()
        )
    }

    internal class SourceFile(val sourceSet: String, val fileName: String, val content: String)
}

class KmpProjectScope internal constructor() {
    internal val root = KmpModuleScope("")
    internal val modules = mutableListOf<ModuleEntry>()

    // root-module (single-module) convenience
    fun commonMain(@Language("kotlin") code: String) = root.commonMain(code)
    fun nonJvmMain(@Language("kotlin") code: String) = root.nonJvmMain(code)
    fun jvmAndNativeMain(@Language("kotlin") code: String) = root.jvmAndNativeMain(code)
    fun commonTest(@Language("kotlin") body: String) = root.commonTest(body)
    fun nonJvmTest(@Language("kotlin") body: String) = root.nonJvmTest(body)
    fun jvmAndNativeTest(@Language("kotlin") body: String) = root.jvmAndNativeTest(body)
    fun jvmTest(@Language("kotlin") body: String) = root.jvmTest(body)

    // a named module depending on other modules by path (e.g. ":service"); `api` only for now, so a
    // consumer transitively sees a service exposed by a provider it depends on.
    fun module(
        name: String,
        dependsOn: List<String> = emptyList(),
        block: KmpModuleScope.() -> Unit,
    ) {
        modules += ModuleEntry(KmpModuleScope(name).apply(block), dependsOn)
    }

    internal class ModuleEntry(val scope: KmpModuleScope, val dependsOn: List<String>)
}

// Builds the project described by [block] on the canonical matrix and runs the consumer's test for each
// target whose source set contains it (compile-only for linuxX64), asserting runtime discovery.
context(project: TestProject)
fun kmpExecutionScenario(block: KmpProjectScope.() -> Unit) {
    val scope = KmpProjectScope().apply(block)
    val multiModule = scope.modules.isNotEmpty()

    val testModule: KmpModuleScope
    if (multiModule) {
        bootstrapMultiModuleProject(scope.modules.map { it.scope.name })
        scope.modules.forEach { entry ->
            val build = multiplatformExecutionBuildFile(entry.scope.name)
            entry.dependsOn.forEach { build.appendText(commonMainDependency("api", it)) }
            entry.scope.files.forEach { writeSource(entry.scope.name, it) }
        }
        testModule = scope.modules.map { it.scope }.single { it.testTargets != null }
    } else {
        bootstrapKotlinMultiplatformExecutionProject()
        scope.root.files.forEach { writeSource("", it) }
        testModule = scope.root
    }

    val prefix = if (testModule.name.isEmpty()) "" else ":${testModule.name}"
    val targets = requireNotNull(testModule.testTargets) { "scenario has no test source set" }

    val tasks = targets.map { t -> "$prefix:${t.testTask ?: t.testCompileTask}" }.toTypedArray()
    gradle(*tasks) {
        targets.forEach { t ->
            if (t.testTask != null) {
                assertTrue(task("$prefix:${t.testTask}")!!.outcome.isPositive, "${t.targetName} test should pass")
            }
        }
    }
}

context(project: TestProject)
private fun writeSource(modulePath: String, source: KmpModuleScope.SourceFile) {
    val dir = if (modulePath.isEmpty()) "src" else "$modulePath/src"
    file("$dir/${source.sourceSet}/kotlin/${source.fileName}").kotlin("sweettests", source.content)
}

private fun commonMainDependency(configuration: String, project: String): String =
    "kotlin { sourceSets.commonMain.dependencies { $configuration(project(\"$project\")) } }\n"

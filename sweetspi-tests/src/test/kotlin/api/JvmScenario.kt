/*
 * Copyright (c) 2026 Oleg Yukhnevich. Use of this source code is governed by the Apache 2.0 license.
 */

package dev.whyoleg.sweetspi.tests.api

import org.intellij.lang.annotations.*
import kotlin.io.path.*
import kotlin.test.*

// ---------------------------------------------------------------------------------------------------
// JVM scenario DSL — single-target analogue of KmpScenario. Express a test as "place Kotlin/Java code
// in one or more modules, build, assert". Two runners: jvmCheckerScenario (compile + diagnostic) and
// jvmExecutionScenario (build + run the consumer's test). Compat-mode META-INF assertions live in
// CompatAssertions.kt and are called from the test body after the scenario returns (the build has run,
// generated files are on disk). All code is passed inline by the calling test.
// ---------------------------------------------------------------------------------------------------

class JvmModuleScope internal constructor(internal val name: String) {
    internal val sources = mutableListOf<SourceFile>()
    internal var hasTest = false
    private var counter = 0

    // Kotlin main source (package `sweettests`, `import dev.whyoleg.sweetspi.*` added).
    fun main(@Language("kotlin") code: String) {
        sources += SourceFile("src/main/kotlin", "Source${counter++}.kt", "package sweettests\n\nimport dev.whyoleg.sweetspi.*\n\n$code")
    }

    // Raw resource written verbatim under src/main/resources/<relativePath> (e.g. a hand-authored
    // META-INF/services/<fqn> for the Java-provider half of PROVIDE_COMPATIBILITY).
    fun resource(relativePath: String, content: String) {
        sources += SourceFile("src/main/resources", relativePath, content)
    }

    // Raw Java source written verbatim under src/main/java/<relativePath> (caller controls package/dirs).
    fun java(relativePath: String, @Language("java") code: String) {
        sources += SourceFile("src/main/java", relativePath, code)
    }

    // Kotlin test body wrapped in a test class; runs in the `:test` task.
    fun test(@Language("kotlin") body: String) {
        check(!hasTest) { "only one test per module is supported" }
        hasTest = true
        sources += SourceFile(
            "src/test/kotlin", "GeneratedTest.kt",
            """
            |package sweettests
            |
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

    internal class SourceFile(val sourceDir: String, val relativePath: String, val content: String)
}

class JvmProjectScope internal constructor() {
    internal val root = JvmModuleScope("")
    internal val modules = mutableListOf<ModuleEntry>()

    // single-module convenience
    fun main(@Language("kotlin") code: String) = root.main(code)
    fun java(relativePath: String, @Language("java") code: String) = root.java(relativePath, code)
    fun resource(relativePath: String, content: String) = root.resource(relativePath, content)
    fun test(@Language("kotlin") body: String) = root.test(body)

    fun module(name: String, dependsOn: List<String> = emptyList(), block: JvmModuleScope.() -> Unit) {
        modules += ModuleEntry(JvmModuleScope(name).apply(block), dependsOn)
    }

    internal class ModuleEntry(val scope: JvmModuleScope, val dependsOn: List<String>)
}

// Builds the JVM project described by [block] and runs the consumer module's `:test`, asserting it passes.
context(project: TestProject)
fun jvmExecutionScenario(block: JvmProjectScope.() -> Unit) {
    val scope = JvmProjectScope().apply(block)

    val testModule: JvmModuleScope
    if (scope.modules.isNotEmpty()) {
        bootstrapMultiModuleProject(scope.modules.map { it.scope.name })
        scope.modules.forEach { entry ->
            val build = jvmBuildFile(entry.scope.name)
            entry.dependsOn.forEach { build.appendText("dependencies { api(project(\"$it\")) }\n") }
            entry.scope.sources.forEach { writeJvmSource(entry.scope.name, it) }
        }
        testModule = scope.modules.map { it.scope }.single { it.hasTest }
    } else {
        bootstrapKotlinJvmProject()
        scope.root.sources.forEach { writeJvmSource("", it) }
        testModule = scope.root
    }

    check(testModule.hasTest) { "scenario has no test body — call test { ... }" }

    val prefix = if (testModule.name.isEmpty()) "" else ":${testModule.name}"
    gradle("$prefix:test") {
        assertTrue(task("$prefix:test")!!.outcome.isPositive, "jvm test should pass")
    }
}

class JvmCheckerScope internal constructor() {
    internal var expectedDiagnostic: String? = null
    fun failsWith(message: String) { expectedDiagnostic = message }
    fun compiles() { expectedDiagnostic = null }
}

// Places [code] in src/main/kotlin/Subject.kt and compiles it, asserting the checker outcome.
context(project: TestProject)
fun jvmCheckerScenario(@Language("kotlin") code: String, block: JvmCheckerScope.() -> Unit) {
    bootstrapKotlinJvmProject()
    file("src/main/kotlin/Subject.kt").kotlin("sweettests", "import dev.whyoleg.sweetspi.*\n\n$code")

    val diagnostic = JvmCheckerScope().apply(block).expectedDiagnostic
    if (diagnostic == null) {
        gradle("compileKotlin", forwardOutput = false) {
            assertTrue(task(":compileKotlin")!!.outcome.isPositive, "should compile")
        }
    } else {
        gradle("compileKotlin", expectFailure = true, forwardOutput = false) {
            assertTrue(output.contains(diagnostic), "expected diagnostic <$diagnostic> in output, but was:\n$output")
        }
    }
}

context(project: TestProject)
private fun writeJvmSource(modulePath: String, source: JvmModuleScope.SourceFile) {
    val base = if (modulePath.isEmpty()) source.sourceDir else "$modulePath/${source.sourceDir}"
    file("$base/${source.relativePath}").writeText(source.content)
}

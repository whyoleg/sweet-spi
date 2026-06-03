# M2 — `Service` compat enum + JVM-test DSL Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make JVM `META-INF/services` generation honor `@Service(javaServiceLoaderCompatibilityMode)` across all three modes (with a FIR checker constraint for `FULL`), and build a `JvmScenario` test DSL + shared compat assertions to test it (pure-JVM and the jvm target of multiplatform projects).

**Architecture:** All compat-mode logic lives in the JVM paths only (klib is mode-agnostic). The IR generator (`ServiceProviderImplementationGenerator`) reads the mode from the resolved service class and, for `FULL`, lists the user `@Provides` class directly under `META-INF/services/X` (synthesizing a public no-arg constructor when the class has only an all-default-param one) instead of the `X$ServiceProvider` wrapper. A FIR rule in `ProvidesChecker` rejects non-class providers for `FULL`. Tests go through a new single-target `JvmScenario` DSL mirroring the existing `KmpScenario`, with compat assertions shared between both DSLs.

**Tech Stack:** Kotlin 2.3.20 compiler plugin (FIR + IR, `compiler-embeddable`), Gradle TestKit functional tests against published dev artifacts.

**Spec:** `docs/superpowers/specs/2026-06-03-m2-compat-enum-design.md`

**Verified compiler APIs (IDEA MCP, kotlin-compiler-embeddable 2.3.20):**
- FIR enum arg: `FirAnnotation.findArgumentByName(name)?.extractEnumValueArgumentInfo()?.enumEntryName` (`FirAnnotationUtils.kt`); handles the deserialized/cross-module case via `FirEnumEntryDeserializedAccessExpression`. Annotation from a symbol: `FirBasedSymbol.getAnnotationByClassId(classId, session)`.
- IR enum arg: annotation `IrConstructorCall.arguments[0] as? IrGetEnumValue`, then `.symbol.owner.name` (`IrGetEnumValue.symbol: IrEnumEntrySymbol`). Consistent with the existing `@Provides` arg read at `ServiceProviderImplementationGenerator.kt:64`.
- Default-param constructor invocation with no args already works in IR today (`ServiceProviderImplementationGenerator.kt:305` `irCall(constructor)`), so a synthetic `constructor(): this()` via `irDelegatingConstructorCall(defaultsCtor)` is sound on the JVM backend.

**Generated-resources location (gradle plugin):** `<module>/build/generated/sweetspi/<sourceSet>/resources/META-INF/services/<serviceId>` where `<sourceSet>` is `main` for a kotlin-jvm module and `jvmMain` for the jvm target of a KMP module (`SweetSpiPlugin.kt:68`).

---

## File structure

**Production (compiler plugin):**
- Create `sweetspi-compiler-plugin/src/main/kotlin/JvmCompatMode.kt` — the `NO/PROVIDE/FULL` enum + `fromEntryName`, shared by FIR & IR.
- Modify `sweetspi-compiler-plugin/src/main/kotlin/SweetNames.kt` — add the `javaServiceLoaderCompatibilityMode` argument `Name`.
- Modify `sweetspi-compiler-plugin/src/main/kotlin/ir/ServiceProviderImplementationGenerator.kt` — per-mode JVM generation + no-arg ctor synthesis.
- Modify `sweetspi-compiler-plugin/src/main/kotlin/fir/ProvidesChecker.kt` — FULL constraint rule + mode reader.
- Modify `sweetspi-compiler-plugin/src/main/kotlin/fir/SweetDiagnostics.kt` — new diagnostic + renderer.

**Tests (sweetspi-tests):**
- Create `sweetspi-tests/src/test/kotlin/api/JvmScenario.kt` — single-target JVM DSL.
- Create `sweetspi-tests/src/test/kotlin/api/CompatAssertions.kt` — shared META-INF assertions.
- Modify `sweetspi-tests/src/test/kotlin/api/KmpScenario.kt` — add jvm-only `jvmTest` hook.
- Create `sweetspi-tests/src/test/kotlin/CompatModeTest.kt` — per-mode generation + Java-consumer tests.
- Create `sweetspi-tests/src/test/kotlin/CompatModeCheckerTest.kt` — FULL constraint checker tests.
- Modify `sweetspi-tests/src/test/kotlin/CheckerTest.kt` — migrate helpers onto `jvmCheckerScenario`.
- Modify `sweetspi-tests/src/test/kotlin/ProviderFormsTest.kt` — migrate `testJvm` onto `jvmExecutionScenario`.

**Docs:**
- Modify `docs/superpowers/specs/2026-06-02-roadmap-design.md` — move usages collector M2→M5, restate M2.

---

## Task 1: `JvmScenario` DSL

**Files:**
- Create: `sweetspi-tests/src/test/kotlin/api/JvmScenario.kt`
- Test: `sweetspi-tests/src/test/kotlin/JvmScenarioTest.kt`

- [ ] **Step 1: Write `JvmScenario.kt`**

```kotlin
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
```

- [ ] **Step 2: Write the smoke test `JvmScenarioTest.kt`**

```kotlin
/*
 * Copyright (c) 2026 Oleg Yukhnevich. Use of this source code is governed by the Apache 2.0 license.
 */

package dev.whyoleg.sweetspi.tests

import dev.whyoleg.sweetspi.tests.api.*
import kotlin.test.*

// Validates the JvmScenario DSL against current (NO_COMPATIBILITY) behavior + an existing diagnostic.
class JvmScenarioTest : TestProjectBasedTest() {

    @Test
    fun executionSingleModule() = context(project) {
        jvmExecutionScenario {
            main(
                """
                @Service interface S
                @Provides(S::class) object Impl : S
                """.trimIndent()
            )
            test(
                """
                assertEquals(1, ServiceLoader.load<S>().toList().size)
                """.trimIndent()
            )
        }
    }

    @Test
    fun checkerDiagnostic() = context(project) {
        jvmCheckerScenario("@Service class NotAService") {
            failsWith("@Service is only applicable to interfaces or abstract classes")
        }
    }
}
```

- [ ] **Step 3: Run the smoke test**

Run: `./gradlew :sweetspi-tests:test --tests "dev.whyoleg.sweetspi.tests.JvmScenarioTest"`
Expected: PASS (both cases — they exercise current behavior).

- [ ] **Step 4: Commit**

```bash
git add sweetspi-tests/src/test/kotlin/api/JvmScenario.kt sweetspi-tests/src/test/kotlin/JvmScenarioTest.kt
git commit -m "test: add single-target JvmScenario DSL"
```

---

## Task 2: Shared compat assertions

**Files:**
- Create: `sweetspi-tests/src/test/kotlin/api/CompatAssertions.kt`
- Test: extend `sweetspi-tests/src/test/kotlin/JvmScenarioTest.kt`

- [ ] **Step 1: Write `CompatAssertions.kt`**

```kotlin
/*
 * Copyright (c) 2026 Oleg Yukhnevich. Use of this source code is governed by the Apache 2.0 license.
 */

package dev.whyoleg.sweetspi.tests.api

import kotlin.io.path.*
import kotlin.test.*

// Reads the compiler-generated META-INF/services/<serviceId> for a built module + source set and
// asserts its entries. Generated resources live at:
//   <module>/build/generated/sweetspi/<sourceSet>/resources/META-INF/services/<serviceId>
// sourceSet is "main" for a kotlin-jvm module and "jvmMain" for the jvm target of a KMP module.
// Call after a jvm/kmp execution scenario has run (the build produced the files on disk).

context(project: TestProject)
private fun servicesPath(modulePath: String, sourceSet: String, serviceId: String) =
    project.projectDirectory.resolve(
        (if (modulePath.isEmpty()) "" else "$modulePath/") +
            "build/generated/sweetspi/$sourceSet/resources/META-INF/services/$serviceId"
    )

// File must exist. If [entries] is given, assert the exact set of (non-blank) lines; if [entryCount] is
// given, assert the number of entries. Pass [entries] for stable ids (e.g. a user class under FULL) and
// [entryCount] for generated wrapper ids whose names are deterministic but verbose.
context(project: TestProject)
fun assertServicesFile(
    modulePath: String = "",
    sourceSet: String = "main",
    serviceId: String,
    entries: Set<String>? = null,
    entryCount: Int? = null,
) {
    val path = servicesPath(modulePath, sourceSet, serviceId)
    assertTrue(path.exists(), "expected generated services file at $path")
    val actual = path.readText().lineSequence().filter { it.isNotBlank() }.toList()
    if (entries != null) assertEquals(entries, actual.toSet(), "entries in META-INF/services/$serviceId")
    if (entryCount != null) assertEquals(entryCount, actual.size, "entry count in META-INF/services/$serviceId")
}

context(project: TestProject)
fun assertNoServicesFile(modulePath: String = "", sourceSet: String = "main", serviceId: String) {
    val path = servicesPath(modulePath, sourceSet, serviceId)
    assertFalse(path.exists(), "expected NO generated services file at $path")
}
```

- [ ] **Step 2: Extend the smoke test to assert current NO-mode generation**

Add to `JvmScenarioTest`:

```kotlin
    @Test
    fun servicesFileForNoCompatibility() = context(project) {
        jvmExecutionScenario {
            main(
                """
                @Service interface S
                @Provides(S::class) object Impl : S
                """.trimIndent()
            )
            test("assertEquals(1, ServiceLoader.load<S>().toList().size)")
        }
        // current behavior: keyed by the $ServiceProvider nested class, one generated wrapper entry
        assertServicesFile(serviceId = "sweettests.S\$ServiceProvider", entryCount = 1)
        assertNoServicesFile(serviceId = "sweettests.S")
    }
```

- [ ] **Step 3: Run**

Run: `./gradlew :sweetspi-tests:test --tests "dev.whyoleg.sweetspi.tests.JvmScenarioTest"`
Expected: PASS.

- [ ] **Step 4: Commit**

```bash
git add sweetspi-tests/src/test/kotlin/api/CompatAssertions.kt sweetspi-tests/src/test/kotlin/JvmScenarioTest.kt
git commit -m "test: add shared META-INF/services compat assertions"
```

---

## Task 3: `KmpScenario` jvm-only test hook

**Files:**
- Modify: `sweetspi-tests/src/test/kotlin/api/KmpScenario.kt`

- [ ] **Step 1: Add a `Jvm`-only target set and the `jvmTest` hook**

In `KmpScenario.kt`, add a target-set constant next to `JVM_AND_NATIVE` (line 34):

```kotlin
private val JVM_ONLY = setOf(KmpTarget.Jvm)
```

In `KmpModuleScope` (after `jvmAndNativeTest`, line 97), add:

```kotlin
    // jvm-only test source set — runs on the Jvm target only (e.g. plain java.util.ServiceLoader checks)
    fun jvmTest(@Language("kotlin") body: String) = test("jvmTest", JVM_ONLY, body)
```

In `KmpProjectScope` (after the `jvmAndNativeTest` delegate, line 135), add:

```kotlin
    fun jvmTest(@Language("kotlin") body: String) = root.jvmTest(body)
```

- [ ] **Step 2: Run the existing KMP suite to confirm no regression**

Run: `./gradlew :sweetspi-tests:test --tests "dev.whyoleg.sweetspi.tests.TopologyTest"`
Expected: PASS (the hook is additive; nothing uses it yet).

- [ ] **Step 3: Commit**

```bash
git add sweetspi-tests/src/test/kotlin/api/KmpScenario.kt
git commit -m "test: add jvm-only test hook to KmpScenario"
```

---

## Task 4: `JvmCompatMode` enum + argument name + readers

**Files:**
- Create: `sweetspi-compiler-plugin/src/main/kotlin/JvmCompatMode.kt`
- Modify: `sweetspi-compiler-plugin/src/main/kotlin/SweetNames.kt:16`

- [ ] **Step 1: Create `JvmCompatMode.kt`**

```kotlin
/*
 * Copyright (c) 2026 Oleg Yukhnevich. Use of this source code is governed by the Apache 2.0 license.
 */

package dev.whyoleg.sweetspi.compiler

// The JVM `java.util.ServiceLoader` compatibility mode declared by `@Service(javaServiceLoaderCompatibilityMode)`.
// Names mirror the runtime `JavaServiceLoaderCompatibilityMode` enum entries so they can be matched by name
// when read from FIR/IR annotation arguments. Absent / unknown → NO_COMPATIBILITY (the runtime default).
enum class JvmCompatMode {
    NO_COMPATIBILITY,
    PROVIDE_COMPATIBILITY,
    FULL_COMPATIBILITY;

    companion object {
        fun fromEntryName(entryName: String?): JvmCompatMode =
            entries.firstOrNull { it.name == entryName } ?: NO_COMPATIBILITY
    }
}
```

- [ ] **Step 2: Add the argument `Name` to `SweetNames`**

In `SweetNames.kt`, after the `ProvidesServiceArgument` line (line 16), add:

```kotlin
    // name of the `javaServiceLoaderCompatibilityMode` argument of `@Service(...)`
    val ServiceCompatModeArgument = Name.identifier("javaServiceLoaderCompatibilityMode")
```

- [ ] **Step 3: Compile the plugin**

Run: `./gradlew :sweetspi-compiler-plugin:compileKotlin`
Expected: BUILD SUCCESSFUL (with `-Werror`).

- [ ] **Step 4: Commit**

```bash
git add sweetspi-compiler-plugin/src/main/kotlin/JvmCompatMode.kt sweetspi-compiler-plugin/src/main/kotlin/SweetNames.kt
git commit -m "feat(compiler): add JvmCompatMode + @Service compat-mode argument name"
```

---

## Task 5: `FULL_COMPATIBILITY` generation (IR)

For a `FULL` service the JVM generator must list the user `@Provides` class directly under
`META-INF/services/X` (not the `X$ServiceProvider` wrapper, which is skipped).

**Files:**
- Modify: `sweetspi-compiler-plugin/src/main/kotlin/ir/ServiceProviderImplementationGenerator.kt:58-106`
- Test: `sweetspi-tests/src/test/kotlin/CompatModeTest.kt`

- [ ] **Step 1: Write the failing test `CompatModeTest.kt`**

```kotlin
/*
 * Copyright (c) 2026 Oleg Yukhnevich. Use of this source code is governed by the Apache 2.0 license.
 */

package dev.whyoleg.sweetspi.tests

import dev.whyoleg.sweetspi.tests.api.*
import kotlin.test.*

// JVM compat-mode generation: which META-INF/services file is written, with which entries, per
// @Service(javaServiceLoaderCompatibilityMode). NO/PROVIDE generate identically (X$ServiceProvider);
// FULL lists the user class directly under X so plain java.util.ServiceLoader works.
class CompatModeTest : TestProjectBasedTest() {

    @Test
    fun fullCompatibility_listsUserClassUnderServiceId() = context(project) {
        jvmExecutionScenario {
            main(
                """
                @Service(JavaServiceLoaderCompatibilityMode.FULL_COMPATIBILITY)
                interface S
                @Provides(S::class) class Impl : S
                """.trimIndent()
            )
            test(
                """
                val providers = ServiceLoader.load<S>().toList()
                assertEquals(1, providers.size)
                assertTrue(providers.single().value is Impl)
                """.trimIndent()
            )
        }
        assertServicesFile(serviceId = "sweettests.S", entries = setOf("sweettests.Impl"))
        assertNoServicesFile(serviceId = "sweettests.S\$ServiceProvider")
    }
}
```

- [ ] **Step 2: Run to verify it fails**

Run: `./gradlew :sweetspi-tests:test --tests "dev.whyoleg.sweetspi.tests.CompatModeTest"`
Expected: FAIL — current code writes `S$ServiceProvider`, so the runtime `FULL` path (`loadJavaSequence` on `S`) finds nothing and/or the assertion on `META-INF/services/sweettests.S` fails.

- [ ] **Step 3: Add the IR mode reader + restructure `process()`**

No new imports needed — `JvmCompatMode` (via `dev.whyoleg.sweetspi.compiler.*`) and `IrGetEnumValue` (via `org.jetbrains.kotlin.ir.expressions.*`) are already covered by the file's existing wildcard imports.

Add a private mode reader inside the class (e.g. after the `regularParameters` helper / near the other private helpers):

```kotlin
    // Reads @Service(javaServiceLoaderCompatibilityMode) off the service class; absent → NO_COMPATIBILITY.
    private fun IrClass.jvmCompatMode(): JvmCompatMode {
        val entryName = getAnnotation(SweetNames.Service.asSingleFqName())
            ?.let { it.arguments.getOrNull(0) as? IrGetEnumValue }
            ?.symbol?.owner?.name?.asString()
        return JvmCompatMode.fromEntryName(entryName)
    }
```

Replace the body of `process()` from the `serviceTypes.forEach { serviceType ->` block (lines 67-105) so the wrapper is built per-branch instead of unconditionally. The new `forEach` body:

```kotlin
        serviceTypes.forEach { serviceType ->
            val serviceClassId = serviceType.classOrFail.owner.classIdOrFail
            val serviceClassSymbol = finder.findClass(serviceClassId) ?: error("Service class ($serviceClassId) not found")
            val providerClassSymbol =
                serviceClassSymbol.owner.findDeclaration<IrClass> { it.name == SweetNames.ServiceProviderName }?.symbol
                    ?: error("Provider class not found for service type: $serviceClassId")
            val providerClassId = providerClassSymbol.owner.classIdOrFail
            val providerType = providerClassSymbol.defaultType

            when (pluginContext) {
                is JvmSweetIrPluginContext  -> when (serviceClassSymbol.owner.jvmCompatMode()) {
                    // NO and PROVIDE generate identically: the wrapper impl listed under X$ServiceProvider.
                    // (PROVIDE differs only at runtime, which also scans X for Java-authored providers.)
                    JvmCompatMode.NO_COMPATIBILITY,
                    JvmCompatMode.PROVIDE_COMPATIBILITY -> {
                        val providerImplClass = buildServiceProviderImpl(declaration, serviceClassId, serviceType, providerType)
                        currentFileAdditionalDeclarations.add(providerImplClass)
                        generatedProviderImplementations.getOrPut(providerClassId, ::mutableSetOf).add(providerImplClass.classIdOrFail)
                    }
                    // FULL: no wrapper; list the user class directly under X. FIR guarantees a non-object class.
                    JvmCompatMode.FULL_COMPATIBILITY    -> {
                        val implClass = declaration as? IrClass
                            ?: error("internal: FULL_COMPATIBILITY requires a class (guaranteed by FIR PROVIDES_FULL_COMPATIBILITY_REQUIRES_CLASS)")
                        ensurePublicNoArgConstructor(implClass)
                        generatedProviderImplementations.getOrPut(serviceClassId, ::mutableSetOf).add(implClass.classIdOrFail)
                    }
                }
                is KlibSweetIrPluginContext -> {
                    // klib is mode-agnostic: always the wrapper + @EagerInitialization registration.
                    val providerImplClass = buildServiceProviderImpl(declaration, serviceClassId, serviceType, providerType)
                    currentFileAdditionalDeclarations.add(providerImplClass)

                    val registryClassSymbol =
                        serviceClassSymbol.owner.findDeclaration<IrClass> { it.name == SweetNames.ServiceProviderRegistryName }?.symbol
                            ?: error("Registry class not found for service type: $serviceClassId")

                    currentFileAdditionalDeclarations += buildServiceProviderImplInitializer(
                        declaration = declaration,
                        serviceClassId = serviceClassId,
                        registryClassSymbol = registryClassSymbol,
                        providerType = providerClassSymbol.defaultType,
                        providerImplClass = providerImplClass,
                    )
                }
            }
        }
```

Add a stub for the constructor synthesis (filled in Task 6) so this task compiles — for now it asserts a real no-arg ctor exists:

```kotlin
    private fun ensurePublicNoArgConstructor(implClass: IrClass) {
        val hasNoArg = implClass.constructors.any { ctor ->
            ctor.visibility == DescriptorVisibilities.PUBLIC && ctor.regularParameters.isEmpty()
        }
        require(hasNoArg) { "internal: no-arg constructor synthesis added in Task 6" }
    }
```

- [ ] **Step 4: Run the test (use an explicit no-arg ctor so the Task-6 stub holds)**

The test's `class Impl : S` has an implicit public no-arg constructor, so `ensurePublicNoArgConstructor` passes.

Run: `./gradlew :sweetspi-tests:test --tests "dev.whyoleg.sweetspi.tests.CompatModeTest"`
Expected: PASS.

- [ ] **Step 5: Confirm no regression in existing KMP/JVM execution**

Run: `./gradlew :sweetspi-tests:test --tests "dev.whyoleg.sweetspi.tests.ProviderFormsTest" --tests "dev.whyoleg.sweetspi.tests.MultipleProvidersTest"`
Expected: PASS (NO/PROVIDE path unchanged; klib path unchanged).

- [ ] **Step 6: Commit**

```bash
git add sweetspi-compiler-plugin/src/main/kotlin/ir/ServiceProviderImplementationGenerator.kt sweetspi-tests/src/test/kotlin/CompatModeTest.kt
git commit -m "feat(compiler): generate META-INF for FULL_COMPATIBILITY services"
```

---

## Task 6: Synthesize a public no-arg constructor for all-default-param `FULL` classes

**Files:**
- Modify: `sweetspi-compiler-plugin/src/main/kotlin/ir/ServiceProviderImplementationGenerator.kt`
- Test: extend `sweetspi-tests/src/test/kotlin/CompatModeTest.kt`

- [ ] **Step 1: Write the failing test**

Add to `CompatModeTest`:

```kotlin
    @Test
    fun fullCompatibility_allDefaultParamConstructor_isLoadableViaPlainJsl() = context(project) {
        jvmExecutionScenario {
            main(
                $$"""
                @Service(JavaServiceLoaderCompatibilityMode.FULL_COMPATIBILITY)
                interface S { fun id(): String }
                @Provides(S::class) class Impl(val tag: String = "default") : S {
                    override fun id() = "Impl($tag)"
                }
                """.trimIndent()
            )
            // load via STOCK java.util.ServiceLoader — requires a public no-arg constructor on Impl
            test(
                """
                val loaded = java.util.ServiceLoader.load(S::class.java).toList()
                assertEquals(1, loaded.size)
                assertEquals("Impl(default)", loaded.single().id())
                """.trimIndent()
            )
        }
        assertServicesFile(serviceId = "sweettests.S", entries = setOf("sweettests.Impl"))
    }
```

- [ ] **Step 2: Run to verify it fails**

Run: `./gradlew :sweetspi-tests:test --tests "dev.whyoleg.sweetspi.tests.CompatModeTest.fullCompatibility_allDefaultParamConstructor_isLoadableViaPlainJsl"`
Expected: FAIL — the Task-5 stub `require(hasNoArg)` throws (the class has only an all-default-param ctor, no public no-arg ctor), failing compilation of the consumer.

- [ ] **Step 3: Implement the synthesis**

No new imports needed — `addConstructor` is already covered by the file's existing `org.jetbrains.kotlin.ir.builders.declarations.*` import.

Replace the `ensurePublicNoArgConstructor` stub with:

```kotlin
    // FULL_COMPATIBILITY needs a public no-arg constructor so java.util.ServiceLoader can instantiate the
    // class. If the class already has one, do nothing. Otherwise (an all-default-param constructor — the
    // only other case the FIR @Provides checker allows) synthesize `constructor(): this()` delegating to
    // it; the JVM backend fills the defaults. JVM-FULL only — klib never reaches here.
    private fun ensurePublicNoArgConstructor(implClass: IrClass) {
        val hasNoArg = implClass.constructors.any { ctor ->
            ctor.visibility == DescriptorVisibilities.PUBLIC && ctor.regularParameters.isEmpty()
        }
        if (hasNoArg) return

        val defaultsCtor = implClass.constructors.first { ctor ->
            ctor.regularParameters.isNotEmpty() && ctor.regularParameters.all { it.defaultValue != null }
        }
        implClass.addConstructor {
            origin = SweetOrigin
            isPrimary = false
            visibility = DescriptorVisibilities.PUBLIC
            returnType = implClass.defaultType
        }.apply {
            body = DeclarationIrBuilder(pluginContext, symbol).irBlockBody {
                +irDelegatingConstructorCall(defaultsCtor)
            }
        }
    }
```

- [ ] **Step 4: Run the test**

Run: `./gradlew :sweetspi-tests:test --tests "dev.whyoleg.sweetspi.tests.CompatModeTest"`
Expected: PASS (both FULL cases).

**Fallback if Step 4 fails** (delegating constructor call with defaulted params does not lower on the JVM backend): do NOT synthesize. Instead, in Task 7's checker, additionally reject all-default-param classes under `FULL` (require an explicit public no-arg constructor) and change this test's `Impl` to `class Impl(val tag: String = "default")` → `class Impl : S { ... }` with an explicit no-arg ctor. Record the limitation in the spec's risk register and proceed.

- [ ] **Step 5: Commit**

```bash
git add sweetspi-compiler-plugin/src/main/kotlin/ir/ServiceProviderImplementationGenerator.kt sweetspi-tests/src/test/kotlin/CompatModeTest.kt
git commit -m "feat(compiler): synthesize no-arg ctor for all-default-param FULL providers"
```

---

## Task 7: FIR checker — `FULL` requires a non-object class

**Files:**
- Modify: `sweetspi-compiler-plugin/src/main/kotlin/fir/SweetDiagnostics.kt`
- Modify: `sweetspi-compiler-plugin/src/main/kotlin/fir/ProvidesChecker.kt`
- Test: `sweetspi-tests/src/test/kotlin/CompatModeCheckerTest.kt`

- [ ] **Step 1: Write the failing checker tests `CompatModeCheckerTest.kt`**

```kotlin
/*
 * Copyright (c) 2026 Oleg Yukhnevich. Use of this source code is governed by the Apache 2.0 license.
 */

package dev.whyoleg.sweetspi.tests

import dev.whyoleg.sweetspi.tests.api.*
import kotlin.test.*

// FULL_COMPATIBILITY only supports a non-object class provider (java.util.ServiceLoader needs a nameable,
// instantiable class). Objects, functions and properties are rejected. NO/PROVIDE keep every form.
class CompatModeCheckerTest : TestProjectBasedTest() {

    private val fullService = "@Service(JavaServiceLoaderCompatibilityMode.FULL_COMPATIBILITY) interface S"
    private val expected = "FULL_COMPATIBILITY"

    @Test
    fun fullRejectsObject() = context(project) {
        jvmCheckerScenario(
            """
            $fullService
            @Provides(S::class) object Impl : S
            """.trimIndent()
        ) { failsWith(expected) }
    }

    @Test
    fun fullRejectsFunction() = context(project) {
        jvmCheckerScenario(
            """
            $fullService
            class ImplC : S
            @Provides(S::class) fun provide(): S = ImplC()
            """.trimIndent()
        ) { failsWith(expected) }
    }

    @Test
    fun fullRejectsProperty() = context(project) {
        jvmCheckerScenario(
            """
            $fullService
            class ImplC : S
            @Provides(S::class) val provide: S = ImplC()
            """.trimIndent()
        ) { failsWith(expected) }
    }

    @Test
    fun fullAcceptsClass() = context(project) {
        jvmCheckerScenario(
            """
            $fullService
            @Provides(S::class) class Impl : S
            """.trimIndent()
        ) { compiles() }
    }

    @Test
    fun noCompatibilityAcceptsObject() = context(project) {
        jvmCheckerScenario(
            """
            @Service interface S
            @Provides(S::class) object Impl : S
            """.trimIndent()
        ) { compiles() }
    }
}
```

- [ ] **Step 2: Run to verify failure**

Run: `./gradlew :sweetspi-tests:test --tests "dev.whyoleg.sweetspi.tests.CompatModeCheckerTest"`
Expected: FAIL — the three `fullRejects*` cases compile today (no constraint yet), so `failsWith` is not satisfied.

- [ ] **Step 3: Add the diagnostic**

In `SweetDiagnostics.kt`, after `PROVIDES_DUPLICATE_SERVICE` (line 36) add:

```kotlin
    val PROVIDES_FULL_COMPATIBILITY_REQUIRES_CLASS by error1<KtElement, String>()
```

In the renderer map (after the `PROVIDES_DUPLICATE_SERVICE` entry, line 81) add:

```kotlin
        map.put(
            SweetDiagnostics.PROVIDES_FULL_COMPATIBILITY_REQUIRES_CLASS,
            "providing for FULL_COMPATIBILITY service ''{0}'' requires an instantiable class; objects, functions and properties are not supported in FULL_COMPATIBILITY mode",
            CommonRenderers.STRING
        )
```

- [ ] **Step 4: Add the mode reader + constraint to `ProvidesChecker`**

No new imports needed — `JvmCompatMode` (via `dev.whyoleg.sweetspi.compiler.*`) and the annotation helpers `findArgumentByName` / `extractEnumValueArgumentInfo` / `getAnnotationByClassId` (all in package `org.jetbrains.kotlin.fir.declarations`, already wildcard-imported) are covered.

Add a file-level helper (next to `isPublicOrInternal`, line 21):

```kotlin
// Reads @Service(javaServiceLoaderCompatibilityMode) off the service symbol; absent → NO_COMPATIBILITY.
// Works cross-module (deserialized annotation arguments are handled by extractEnumValueArgumentInfo).
fun FirClassSymbol<*>.jvmCompatMode(session: FirSession): JvmCompatMode {
    val entry = getAnnotationByClassId(SweetNames.Service, session)
        ?.findArgumentByName(SweetNames.ServiceCompatModeArgument)
        ?.extractEnumValueArgumentInfo()
        ?.enumEntryName
        ?.asString()
    return JvmCompatMode.fromEntryName(entry)
}
```

In the base `check` (inside the `getAnnotationsByClassId(...).forEach { annotation -> ... }` block, after the existing `when { ... }`, around line 62) add the constraint:

```kotlin
            if (serviceSymbol.jvmCompatMode(session) == JvmCompatMode.FULL_COMPATIBILITY) {
                val isInstantiableClass = declaration is FirRegularClass && declaration.classKind == ClassKind.CLASS
                if (!isInstantiableClass) {
                    reporter.reportOn(
                        annotation.source,
                        SweetDiagnostics.PROVIDES_FULL_COMPATIBILITY_REQUIRES_CLASS,
                        serviceSymbol.classId.asFqNameString(),
                    )
                }
            }
```

Note: `ClassKind` is already imported via `org.jetbrains.kotlin.descriptors.*` (line 8). The object case is caught because an `object` has `classKind == ClassKind.OBJECT`; functions/properties are not `FirRegularClass`.

- [ ] **Step 5: Run the checker tests**

Run: `./gradlew :sweetspi-tests:test --tests "dev.whyoleg.sweetspi.tests.CompatModeCheckerTest"`
Expected: PASS (all five cases).

- [ ] **Step 6: Commit**

```bash
git add sweetspi-compiler-plugin/src/main/kotlin/fir/SweetDiagnostics.kt sweetspi-compiler-plugin/src/main/kotlin/fir/ProvidesChecker.kt sweetspi-tests/src/test/kotlin/CompatModeCheckerTest.kt
git commit -m "feat(compiler): FIR constraint — FULL_COMPATIBILITY requires a class provider"
```

---

## Task 8: NO / PROVIDE / FULL functional coverage + cross-module FULL + Java consumer

**Files:**
- Test: extend `sweetspi-tests/src/test/kotlin/CompatModeTest.kt` and `CompatModeCheckerTest.kt`

- [ ] **Step 1: Add NO + PROVIDE generation tests to `CompatModeTest`**

```kotlin
    @Test
    fun noCompatibility_keysByServiceProviderNested() = context(project) {
        jvmExecutionScenario {
            main(
                """
                @Service interface S
                @Provides(S::class) object Impl : S
                """.trimIndent()
            )
            test("assertEquals(1, ServiceLoader.load<S>().toList().size)")
        }
        assertServicesFile(serviceId = "sweettests.S\$ServiceProvider", entryCount = 1)
        assertNoServicesFile(serviceId = "sweettests.S")
    }

    @Test
    fun provideCompatibility_kotlinViaNested_javaViaServiceId() = context(project) {
        jvmExecutionScenario {
            main(
                """
                @Service(JavaServiceLoaderCompatibilityMode.PROVIDE_COMPATIBILITY)
                interface S { fun id(): String }
                @Provides(S::class) object KotlinImpl : S { override fun id() = "kotlin" }
                """.trimIndent()
            )
            // a Java-authored provider registered the plain JSL way (no @Provides)
            java(
                "sweettests/JavaImpl.java",
                """
                package sweettests;
                public final class JavaImpl implements S {
                    public JavaImpl() {}
                    @Override public String id() { return "java"; }
                }
                """.trimIndent()
            )
            resource("META-INF/services/sweettests.S", "sweettests.JavaImpl")
            test(
                """
                val ids = ServiceLoader.load<S>().map { it.value.id() }.toSet()
                assertEquals(setOf("kotlin", "java"), ids)
                """.trimIndent()
            )
        }
        // Kotlin @Provides generated only into the nested key; the X file is the hand-authored Java one.
        assertServicesFile(serviceId = "sweettests.S\$ServiceProvider", entryCount = 1)
    }
```

- [ ] **Step 2: Add the FULL Java-source consumer test to `CompatModeTest`**

```kotlin
    @Test
    fun fullCompatibility_javaConsumerLoadsViaPlainJsl() = context(project) {
        jvmExecutionScenario {
            main(
                """
                @Service(JavaServiceLoaderCompatibilityMode.FULL_COMPATIBILITY)
                interface S { fun id(): String }
                @Provides(S::class) class Impl : S { override fun id() = "impl" }
                """.trimIndent()
            )
            // a Java consumer using stock java.util.ServiceLoader, invoked from the Kotlin test
            java(
                "sweettests/JavaConsumer.java",
                """
                package sweettests;
                import java.util.ServiceLoader;
                public final class JavaConsumer {
                    public static int count() {
                        int n = 0;
                        for (S s : ServiceLoader.load(S.class)) n++;
                        return n;
                    }
                }
                """.trimIndent()
            )
            test("assertEquals(1, JavaConsumer.count())")
        }
    }
```

- [ ] **Step 3: Add a cross-module FULL checker test to `CompatModeCheckerTest`**

This proves the FULL constraint reads the service's compat mode from a dependency module.

```kotlin
    @Test
    fun fullRejectsObjectAcrossModules() = context(project) {
        // service in :service (FULL), bad object provider in :provider depending on it → compile must fail
        bootstrapMultiModuleProject(listOf("service", "provider"))
        jvmBuildFile("service")
        file("service/src/main/kotlin/S.kt").kotlin(
            "sweettests",
            "import dev.whyoleg.sweetspi.*\n\n@Service(JavaServiceLoaderCompatibilityMode.FULL_COMPATIBILITY) interface S"
        )
        jvmBuildFile("provider").appendText("dependencies { api(project(\":service\")) }\n")
        file("provider/src/main/kotlin/Impl.kt").kotlin(
            "sweettests",
            "import dev.whyoleg.sweetspi.*\n\n@Provides(S::class) object Impl : S"
        )
        gradle(":provider:compileKotlin", expectFailure = true, forwardOutput = false) {
            assertTrue(output.contains("FULL_COMPATIBILITY"), "expected FULL_COMPATIBILITY diagnostic, was:\n$output")
        }
    }
```

- [ ] **Step 4: Run**

Run: `./gradlew :sweetspi-tests:test --tests "dev.whyoleg.sweetspi.tests.CompatModeTest" --tests "dev.whyoleg.sweetspi.tests.CompatModeCheckerTest"`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add sweetspi-tests/src/test/kotlin/CompatModeTest.kt sweetspi-tests/src/test/kotlin/CompatModeCheckerTest.kt
git commit -m "test: NO/PROVIDE/FULL functional coverage + cross-module FULL + Java consumer"
```

---

## Task 9: MPP smoke test — jvm target honors the compat mode

**Files:**
- Test: `sweetspi-tests/src/test/kotlin/CompatModeMultiplatformTest.kt`

- [ ] **Step 1: Write the test**

```kotlin
/*
 * Copyright (c) 2026 Oleg Yukhnevich. Use of this source code is governed by the Apache 2.0 license.
 */

package dev.whyoleg.sweetspi.tests

import dev.whyoleg.sweetspi.tests.api.*
import kotlin.test.*

// The compat mode is a JVM concern; this confirms the jvm() target of a multiplatform project honors
// FULL_COMPATIBILITY (loadable via stock java.util.ServiceLoader) and that generation lands in jvmMain.
class CompatModeMultiplatformTest : TestProjectBasedTest() {

    @Test
    fun fullCompatibilityOnJvmTarget() = context(project) {
        kmpExecutionScenario {
            commonMain(
                """
                @Service(JavaServiceLoaderCompatibilityMode.FULL_COMPATIBILITY)
                interface S { fun id(): String }
                @Provides(S::class) class Impl : S { override fun id() = "impl" }
                """.trimIndent()
            )
            jvmTest(
                """
                val loaded = java.util.ServiceLoader.load(S::class.java).toList()
                assertEquals(1, loaded.size)
                assertEquals("impl", loaded.single().id())
                """.trimIndent()
            )
        }
        assertServicesFile(sourceSet = "jvmMain", serviceId = "sweettests.S", entries = setOf("sweettests.Impl"))
    }
}
```

Note: `kmpExecutionScenario` writes sources under `src/<sourceSet>/kotlin` with package `sweettests` (see `writeSource` in `KmpScenario.kt`), so the generated service id is `sweettests.S`.

- [ ] **Step 2: Run**

Run: `./gradlew :sweetspi-tests:test --tests "dev.whyoleg.sweetspi.tests.CompatModeMultiplatformTest"`
Expected: PASS (the `jvmTest` source set runs only on the Jvm target).

- [ ] **Step 3: Commit**

```bash
git add sweetspi-tests/src/test/kotlin/CompatModeMultiplatformTest.kt
git commit -m "test: MPP jvm target honors FULL_COMPATIBILITY"
```

---

## Task 10: Migrate existing pure-JVM tests onto the DSL

`CheckerTest` and `ProviderFormsTest.testJvm` are the only pure-JVM tests (`MultiModuleServiceTest` was already consolidated into `TopologyTest`/`MultipleProvidersTest`). Migrate by re-pointing their bootstrap at the DSL — all assertions stay identical.

**Files:**
- Modify: `sweetspi-tests/src/test/kotlin/CheckerTest.kt:13-49`
- Modify: `sweetspi-tests/src/test/kotlin/ProviderFormsTest.kt:16-49`
- Modify: `sweetspi-tests/src/test/kotlin/JvmScenarioTest.kt` (drop redundant smoke cases now covered by real tests)

- [ ] **Step 1: Repoint `CheckerTest` helpers to `jvmCheckerScenario`**

Replace the two private helpers (`assertCompilationError` lines 13-32, `assertCompiles` lines 34-49) with:

```kotlin
    private fun assertCompilationError(
        @Language("kotlin") code: String,
        expectedMessage: String,
    ) = context(project) {
        jvmCheckerScenario(code) { failsWith(expectedMessage) }
    }

    private fun assertCompiles(
        @Language("kotlin") code: String,
    ) = context(project) {
        jvmCheckerScenario(code) { compiles() }
    }
```

All 22 case methods (lines 51+) are unchanged.

- [ ] **Step 2: Run the full CheckerTest**

Run: `./gradlew :sweetspi-tests:test --tests "dev.whyoleg.sweetspi.tests.CheckerTest"`
Expected: PASS (all cases).

- [ ] **Step 3: Migrate `ProviderFormsTest.testJvm`**

Replace `testJvm` (lines 16-49) with:

```kotlin
    @Test
    fun testJvm() = context(project) {
        jvmExecutionScenario {
            main(
                """
                @Service interface SimpleService
                @Provides(SimpleService::class) object SimpleServiceImpl : SimpleService
                """.trimIndent()
            )
            test(
                """
                val providers = ServiceLoader.load<SimpleService>().toList()
                assertEquals(1, providers.size)
                val provider = providers.single()
                assertEquals(SimpleServiceImpl::class, provider.type)
                assertEquals(false, provider.isInitialized())
                assertEquals(SimpleServiceImpl, provider.value)
                assertEquals(true, provider.isInitialized())
                """.trimIndent()
            )
        }
    }
```

- [ ] **Step 4: Drop the now-redundant smoke cases from `JvmScenarioTest`**

`JvmScenarioTest.executionSingleModule` / `checkerDiagnostic` / `servicesFileForNoCompatibility` are now covered by `CompatModeTest` + `CheckerTest`. Delete `JvmScenarioTest.kt`.

```bash
git rm sweetspi-tests/src/test/kotlin/JvmScenarioTest.kt
```

- [ ] **Step 5: Run the migrated tests**

Run: `./gradlew :sweetspi-tests:test --tests "dev.whyoleg.sweetspi.tests.CheckerTest" --tests "dev.whyoleg.sweetspi.tests.ProviderFormsTest"`
Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add sweetspi-tests/src/test/kotlin/CheckerTest.kt sweetspi-tests/src/test/kotlin/ProviderFormsTest.kt
git commit -m "test: migrate pure-JVM tests onto JvmScenario DSL"
```

---

## Task 11: Roadmap doc update + full-suite verification

**Files:**
- Modify: `docs/superpowers/specs/2026-06-02-roadmap-design.md`

- [ ] **Step 1: Update the roadmap doc**

In `2026-06-02-roadmap-design.md`:

1. In the **Milestone table** (lines 81-91), change the M2 row `Goal` to drop the usage-collection clause and the M5 row to add it. New M2 row:

```markdown
| M2 | Honor `Service` compat enum in IR/resources + JVM-test DSL | M1 | feature | planned |
```

New M5 row (append the collector to its goal):

```markdown
| M5 | `module-info.java` in gradle plugin (+ `ServiceLoader` usage collection) | M1, M2 | feature | planned |
```

2. In the **M2 section** (lines 231-262), delete the `ServiceLoaderUsagesCollector` bullet (lines 249-256) and append a one-line note under the heading:

```markdown
**Moved to M5 (2026-06-03):** `ServiceLoaderUsagesCollector` belongs with the JVM `module-info` `# uses`
work (its only consumer) and carries the reified-`inline` extraction risk; implemented in M5.
```

3. In the **M5 section** (lines 308-329), add a scope bullet:

```markdown
- **Implement `ServiceLoaderUsagesCollector`** (moved from M2): walk IR bodies, match calls whose
  `callableId` is in `SweetNames.AllServiceLoaderCalls`, extract the service `ClassId` from the reified
  type / `KClass` argument (confirm reified-`inline` survival via IDEA MCP), feeding the `# uses` section.
```

4. In the **M1c section** "Decisions" (line 205), append:

```markdown
**Resolved in M2 (2026-06-03):** built a separate single-target `JvmScenario` DSL + shared
`CompatAssertions` used by both DSLs; migrated `CheckerTest` and `ProviderFormsTest.testJvm` onto it.
```

- [ ] **Step 2: Run the full test suite**

Run: `./gradlew :sweetspi-tests:test`
Expected: BUILD SUCCESSFUL, 0 skipped beyond the pre-existing known-limitation `@Ignore`s.

- [ ] **Step 3: Verify the plugin compiles clean**

Run: `./gradlew :sweetspi-compiler-plugin:compileKotlin`
Expected: BUILD SUCCESSFUL with `-Werror`.

- [ ] **Step 4: Commit**

```bash
git add docs/superpowers/specs/2026-06-02-roadmap-design.md
git commit -m "docs: move usage collector M2->M5; mark M2 scope (compat enum + JVM-test DSL)"
```

---

## Notes for the implementer

- **Compat mode is JVM-only.** Never branch on it in the klib path; the klib generator stays exactly as it is (wrapper + `@EagerInitialization` registry, mode-agnostic).
- **The `$ServiceProvider` nested class is still generated by FIR for every service**, including FULL ones. On JVM-FULL it is simply not listed in `META-INF` (the wrapper impl is also not built). Leaving the empty nested class is intentional and harmless; do not try to suppress it.
- **The synthesized no-arg constructor is IR-generated**, so it appears in `.class` bytecode (visible to `java.util.ServiceLoader`) but not in Kotlin metadata/ABI — so it does not affect ABI validation dumps.
- **Service ids use `$`** for the nested class: in Kotlin test strings write `"sweettests.S\$ServiceProvider"`.
- If a step's exact compiler-API call does not resolve, verify via the IDEA MCP server (`search_symbol` / `read_file`, `projectPath=/Users/Oleg.Yukhnevich/Projects/whyoleg/sweet-spi`) — never unzip jars.

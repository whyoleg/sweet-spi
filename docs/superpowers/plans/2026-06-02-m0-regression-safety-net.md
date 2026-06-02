# M0 — Regression Safety Net Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Lock current cross-module SPI discovery and checker behavior behind tests so the M1 FIR/IR refactor can be proven behavior-preserving.

**Architecture:** Extract small build-file helpers (`jvmModule` / `multiplatformModule`) that hide the
`build.gradle.kts` boilerplate (plugins, targets, ABI validation, dependencies) for both root and
sub-modules; reuse them in the existing single-module bootstrap functions. Add multi-module support
to the harness (`settings.gradle.kts` includes). Then add JVM and Kotlin-Multiplatform functional
tests where a `@Service` lives in one module, its `@Provides` impl in a second, and the consuming
`ServiceLoader.load` in a third — with the service type reaching the consumer only *transitively*
(the "library" promise). Finally, add a KMP variant of the checker tests proving the FIR diagnostics
fire on common-code compilation, not just JVM.

**Tech Stack:** Kotlin 2.3.20, Gradle TestKit (`GradleRunner`), JUnit5 (`kotlin("test-junit5")` + `useJUnitPlatform()`), context parameters (`-Xcontext-parameters`).

---

## Design constraints from review

- **Do not abstract over the JVM-vs-Multiplatform axis.** Keep two separate helpers, `jvmModule` and
  `multiplatformModule`. No sealed `KotlinSetup` type.
- **Helpers hide build-file logic only.** Tests must keep writing source files with explicit
  `file("…/src/main/kotlin/…")` / `file("…/src/commonMain/kotlin/…")` calls and must keep explicit
  `gradle(":consumer:test")` / `gradle(":consumer:jvmTest", …)` calls, so it stays obvious from the
  test where Kotlin files live and which tasks run (as the existing tests do). No `moduleSource`
  helper, no task-name derivation helper.
- The helpers also back the existing `bootstrapKotlinJvmProject` / `bootstrapKotlinMultiplatformProject`.

---

## Background the engineer needs

- **All tests live in `sweetspi-tests`** and run *real* Gradle builds via `GradleRunner` against
  **dev artifacts** of `sweetspi-runtime` / `sweetspi-compiler-plugin` / `sweetspi-gradle-plugin`.
  The `:sweetspi-tests:test` task auto-builds and resolves those artifacts (via the
  `devArtifactsResolver` configuration) and passes them to the spawned builds as system properties.
  **You never publish anything manually** — just run the test task.
- **Run a single test class:**
  `./gradlew :sweetspi-tests:test --tests "dev.whyoleg.sweetspi.tests.<ClassName>"`
  **Run a single method:** append `.<methodName>` to the filter.
  These tests are **slow** (each spawns Gradle; KMP/native targets download the Kotlin/Native
  toolchain on first run). Multi-minute runs are normal.
- **Test harness API** (all in `sweetspi-tests/src/test/kotlin/api/TestProject.kt`, used via
  `context(project: TestProject)`):
  - `bootstrapKotlinJvmProject()` / `bootstrapKotlinMultiplatformProject()` — write a *single-module*
    root project. Both enable ABI validation.
  - `file(path): Path` — resolves under the test project dir, creating parent dirs.
  - `Path.kts(code)` — write a `.gradle.kts` file verbatim.
  - `Path.kotlin(packageName, code)` — write a `.kt` file prefixed with `package $packageName`.
  - `Path.kotlin(code)` — same but package defaults to `sweettests.<testMethodName>`.
  - `gradle(vararg args, expectFailure = false, block)` — run the inner build; `block` receives
    `BuildResult` (use `.output`, `.task(":x")!!.outcome.isPositive`).
  - `String.alignMargin(prefix = "|")` — re-indent multi-line snippets.
- The version catalog written by the harness defines aliases `libs.plugins.kotlin.jvm`,
  `libs.plugins.kotlin.multiplatform`, `libs.plugins.sweetspi`, available in **every** module build
  file of the spawned project.
- **`SweetSpiPlugin` auto-adds the runtime dependency** to `main` (JVM) / `commonMain` (KMP) when
  applied — module build files must NOT add the runtime manually.
- **Decision — no ABI validation in the multi-module test modules.** The single-module bootstraps
  enable it (and require `:updateKotlinAbi`), and there is a known flaky interaction documented in
  `SimpleServiceTest`. M0's multi-module tests are pure execution tests; we omit `abiValidation` to
  keep them robust and fast.
- **Cross-module SPI flow being pinned:** the `$ServiceProvider` nested interface is generated in the
  module that holds `@Service`; the provider-impl class + `META-INF/services` entry (JVM) / eager-init
  registration (klib) are generated in the module that holds `@Provides`; the consumer discovers them
  at runtime. The transitive-library variant makes the consumer depend only on the *provider* module,
  receiving the service type via the provider's `api(project(":service"))`.

---

## File structure

| File | Responsibility | Change |
|------|----------------|--------|
| `sweetspi-tests/src/test/kotlin/api/BuildFiles.kt` | `jvmModule` / `multiplatformModule` build-file generators | **create** |
| `sweetspi-tests/src/test/kotlin/api/TestProject.kt` | project bootstrap + settings/multi-module | **modify**: `includes` param; `bootstrapMultiModuleProject`; refactor the two bootstrap fns onto the new helpers |
| `sweetspi-tests/src/test/kotlin/MultiModuleServiceTest.kt` | JVM + KMP cross-module execution tests | **create** |
| `sweetspi-tests/src/test/kotlin/CheckerKmpTest.kt` | checker diagnostics on KMP common compilation | **create** |

---

## Task 1: Build-file helpers + multi-module harness support

**Files:**
- Create: `sweetspi-tests/src/test/kotlin/api/BuildFiles.kt`
- Modify: `sweetspi-tests/src/test/kotlin/api/TestProject.kt`

- [ ] **Step 1: Create the build-file helpers**

Create `sweetspi-tests/src/test/kotlin/api/BuildFiles.kt`:

```kotlin
/*
 * Copyright (c) 2026 Oleg Yukhnevich. Use of this source code is governed by the Apache 2.0 license.
 */

package dev.whyoleg.sweetspi.tests.api

private const val ABI_IMPORT = "import org.jetbrains.kotlin.gradle.dsl.abi.ExperimentalAbiValidation"

// Writes <path>/build.gradle.kts for a kotlin-jvm + sweetspi module. path="" = root module.
// `dependencies` are full configuration lines, e.g. "api(project(\":service\"))".
context(project: TestProject)
fun jvmModule(
    path: String = "",
    abiValidation: Boolean = false,
    dependencies: List<String> = emptyList(),
) {
    writeBuildFile(path, buildString {
        if (abiValidation) appendLine(ABI_IMPORT).appendLine()
        appendLine("plugins {")
        appendLine("    alias(libs.plugins.kotlin.jvm)")
        appendLine("    alias(libs.plugins.sweetspi)")
        appendLine("}")
        if (abiValidation) {
            appendLine("@OptIn(ExperimentalAbiValidation::class)")
            appendLine("kotlin { abiValidation { enabled = true } }")
        }
        if (dependencies.isNotEmpty()) {
            appendLine("dependencies {")
            dependencies.forEach { appendLine("    $it") }
            appendLine("}")
        }
    })
}

// Writes <path>/build.gradle.kts for a kotlin-multiplatform + sweetspi module. path="" = root module.
// `targets` are target names rendered as calls, e.g. listOf("jvm", "macosArm64") -> jvm(); macosArm64().
// Dependency lists are full configuration lines placed in commonMain/commonTest source-set deps.
context(project: TestProject)
fun multiplatformModule(
    path: String = "",
    targets: List<String> = emptyList(),
    abiValidation: Boolean = false,
    commonMainDependencies: List<String> = emptyList(),
    commonTestDependencies: List<String> = emptyList(),
) {
    writeBuildFile(path, buildString {
        if (abiValidation) appendLine(ABI_IMPORT).appendLine()
        appendLine("plugins {")
        appendLine("    alias(libs.plugins.kotlin.multiplatform)")
        appendLine("    alias(libs.plugins.sweetspi)")
        appendLine("}")
        if (abiValidation) appendLine("@OptIn(ExperimentalAbiValidation::class)")
        appendLine("kotlin {")
        if (abiValidation) appendLine("    abiValidation { enabled = true }")
        targets.forEach { appendLine("    $it()") }
        if (commonMainDependencies.isNotEmpty()) {
            appendLine("    sourceSets.commonMain.dependencies {")
            commonMainDependencies.forEach { appendLine("        $it") }
            appendLine("    }")
        }
        if (commonTestDependencies.isNotEmpty()) {
            appendLine("    sourceSets.commonTest.dependencies {")
            commonTestDependencies.forEach { appendLine("        $it") }
            appendLine("    }")
        }
        appendLine("}")
    })
}

context(project: TestProject)
private fun writeBuildFile(path: String, content: String) {
    val filePath = if (path.isEmpty()) "build.gradle.kts" else "$path/build.gradle.kts"
    file(filePath).kts(content)
}
```

- [ ] **Step 2: Add multi-module settings support to `TestProject.kt`**

In `sweetspi-tests/src/test/kotlin/api/TestProject.kt`, replace the private `bootstrapGradleProject`
function (currently lines ~96-150) with this version — the only change is the new `includes`
parameter and the appended `include(...)` lines:

```kotlin
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
        """.trimMargin("|        ") // to be aligned in `settings.gradle.kts`

    val includeStatements = includes.joinToString("\n") { "include(\"$it\")" }

    file("settings.gradle.kts").kts(
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
        |$includeStatements
        |
        """.trimMargin()
    )
}
```

Then add a public multi-module bootstrap right after `bootstrapKotlinMultiplatformProject`:

```kotlin
// Multi-module project: writes shared settings (with `include(...)`), properties and version catalog.
// The caller writes each module's build file (jvmModule/multiplatformModule) + sources via file(...).
// No root build.gradle.kts is written and ABI validation is intentionally not enabled here.
context(project: TestProject)
fun bootstrapMultiModuleProject(modules: List<String>) {
    bootstrapGradleProject(includes = modules)
}
```

- [ ] **Step 3: Refactor the existing bootstrap functions onto the helpers**

In the same file, replace the bodies of `bootstrapKotlinJvmProject` and
`bootstrapKotlinMultiplatformProject` (currently lines ~46-94) with these — behavior-equivalent to the
hand-written build files they replace:

```kotlin
context(project: TestProject)
fun bootstrapKotlinJvmProject() {
    bootstrapGradleProject()
    jvmModule(abiValidation = true, dependencies = listOf("implementation(kotlin(\"test\"))"))
}

context(project: TestProject)
fun bootstrapKotlinMultiplatformProject() {
    bootstrapGradleProject()
    multiplatformModule(abiValidation = true, commonTestDependencies = listOf("implementation(kotlin(\"test\"))"))
}
```

- [ ] **Step 4: Verify the refactor is behavior-preserving**

Run: `./gradlew :sweetspi-tests:test --tests "dev.whyoleg.sweetspi.tests.CheckerTest" --tests "dev.whyoleg.sweetspi.tests.SimpleServiceTest.testJvm"`
Expected: BUILD SUCCESSFUL. (`CheckerTest` exercises the refactored `jvmModule` via
`bootstrapKotlinJvmProject`; `SimpleServiceTest.testJvm` exercises a full JVM compile+run.) The full
multiplatform `SimpleServiceTest` is verified in Task 5.

- [ ] **Step 5: Commit**

```bash
git add sweetspi-tests/src/test/kotlin/api/BuildFiles.kt sweetspi-tests/src/test/kotlin/api/TestProject.kt
git commit -m "test(api): extract jvm/multiplatform build-file helpers + multi-module support"
```

---

## Task 2: JVM cross-module execution test

**Files:**
- Create: `sweetspi-tests/src/test/kotlin/MultiModuleServiceTest.kt`

- [ ] **Step 1: Write the failing test**

Create `sweetspi-tests/src/test/kotlin/MultiModuleServiceTest.kt`:

```kotlin
/*
 * Copyright (c) 2026 Oleg Yukhnevich. Use of this source code is governed by the Apache 2.0 license.
 */

package dev.whyoleg.sweetspi.tests

import dev.whyoleg.sweetspi.tests.api.*
import kotlin.test.*

// Shared subject code, placed into different source sets by each test.
private val SERVICE_CODE = """
    import dev.whyoleg.sweetspi.*
    @Service interface GreetingService { fun greet(): String }
""".trimIndent()

private val PROVIDER_CODE = """
    import dev.whyoleg.sweetspi.*
    import sweettests.service.*
    @Provides(GreetingService::class) object EnglishGreeting : GreetingService {
        override fun greet(): String = "hello"
    }
""".trimIndent()

private val CONSUMER_TEST_CODE = """
    import dev.whyoleg.sweetspi.*
    import sweettests.service.*
    import kotlin.test.*
    class ConsumerTest {
        @Test
        fun test() {
            val providers = ServiceLoader.load<GreetingService>().toList()
            assertEquals(1, providers.size)
            assertEquals("hello", providers.single().value.greet())
        }
    }
""".trimIndent()

class MultiModuleServiceTest : TestProjectBasedTest() {

    // :consumer depends only on :provider; the service type reaches it transitively via
    // :provider's `api(project(":service"))` — the "library" promise.
    @Test
    fun testJvm() = context(project) {
        bootstrapMultiModuleProject(listOf("service", "provider", "consumer"))

        jvmModule("service")
        file("service/src/main/kotlin/GreetingService.kt").kotlin("sweettests.service", SERVICE_CODE)

        jvmModule("provider", dependencies = listOf("api(project(\":service\"))"))
        file("provider/src/main/kotlin/EnglishGreeting.kt").kotlin("sweettests.provider", PROVIDER_CODE)

        jvmModule(
            "consumer",
            dependencies = listOf(
                "implementation(project(\":provider\"))",
                "implementation(kotlin(\"test\"))",
            )
        )
        file("consumer/src/test/kotlin/ConsumerTest.kt").kotlin("sweettests.consumer", CONSUMER_TEST_CODE)

        gradle(":consumer:test") {
            assertTrue(task(":consumer:test")!!.outcome.isPositive)
        }
    }
}
```

- [ ] **Step 2: Run the test to verify it passes**

Run: `./gradlew :sweetspi-tests:test --tests "dev.whyoleg.sweetspi.tests.MultiModuleServiceTest.testJvm"`
Expected: PASS. (Cross-module discovery already works; this test pins that behavior.)

- [ ] **Step 3: Commit**

```bash
git add sweetspi-tests/src/test/kotlin/MultiModuleServiceTest.kt
git commit -m "test: pin JVM cross-module SPI discovery (transitive library)"
```

---

## Task 3: KMP cross-module execution test (jvm + macosArm64)

**Files:**
- Modify: `sweetspi-tests/src/test/kotlin/MultiModuleServiceTest.kt`

- [ ] **Step 1: Add the KMP test**

Append this method inside `MultiModuleServiceTest` (it reuses the `SERVICE_CODE` / `PROVIDER_CODE` /
`CONSUMER_TEST_CODE` constants — only the source-set directories and the run tasks differ):

```kotlin
    @Test
    fun testMultiplatform() = context(project) {
        bootstrapMultiModuleProject(listOf("service", "provider", "consumer"))
        val targets = listOf("jvm", "macosArm64")

        multiplatformModule("service", targets = targets)
        file("service/src/commonMain/kotlin/GreetingService.kt").kotlin("sweettests.service", SERVICE_CODE)

        multiplatformModule(
            "provider",
            targets = targets,
            commonMainDependencies = listOf("api(project(\":service\"))")
        )
        file("provider/src/commonMain/kotlin/EnglishGreeting.kt").kotlin("sweettests.provider", PROVIDER_CODE)

        multiplatformModule(
            "consumer",
            targets = targets,
            commonMainDependencies = listOf("implementation(project(\":provider\"))"),
            commonTestDependencies = listOf("implementation(kotlin(\"test\"))")
        )
        file("consumer/src/commonTest/kotlin/ConsumerTest.kt").kotlin("sweettests.consumer", CONSUMER_TEST_CODE)

        gradle(":consumer:jvmTest", ":consumer:macosArm64Test") {
            assertTrue(task(":consumer:jvmTest")!!.outcome.isPositive)
            assertTrue(task(":consumer:macosArm64Test")!!.outcome.isPositive)
        }
    }
```

- [ ] **Step 2: Run the test to verify it passes**

Run: `./gradlew :sweetspi-tests:test --tests "dev.whyoleg.sweetspi.tests.MultiModuleServiceTest.testMultiplatform"`
Expected: PASS. (Pins cross-module klib eager-init registration on native + JVM discovery.)

If `:consumer:macosArm64Test` fails for a reason **unrelated** to providers (toolchain/linker
flakiness), do NOT mask it — capture the output and report it as an M0 finding per the spec. If it
fails because providers are *not discovered cross-module on native*, that is exactly the behavior M0
exists to surface — report it before proceeding to M1.

- [ ] **Step 3: Commit**

```bash
git add sweetspi-tests/src/test/kotlin/MultiModuleServiceTest.kt
git commit -m "test: pin KMP cross-module SPI discovery (jvm + macosArm64)"
```

---

## Task 4: KMP checker tests (diagnostics on common compilation)

**Files:**
- Create: `sweetspi-tests/src/test/kotlin/CheckerKmpTest.kt`

**Rationale for a representative subset:** the FIR checkers are registered with `MppCheckerKind.Common`
(see `fir/SweetFirExtensionRegistrar.kt` / `ProvidesChecker`), so the *same* code path runs for JVM and
KMP. The goal here is to prove the checkers actually fire during multiplatform **common** compilation,
not to re-assert every rule. We cover one `@Service` rule, the service/provides cross-check, two
`@Provides`-on-class rules, the callable-placement rule, and one positive control.

- [ ] **Step 1: Write the tests**

Create `sweetspi-tests/src/test/kotlin/CheckerKmpTest.kt`:

```kotlin
/*
 * Copyright (c) 2026 Oleg Yukhnevich. Use of this source code is governed by the Apache 2.0 license.
 */

package dev.whyoleg.sweetspi.tests

import dev.whyoleg.sweetspi.tests.api.*
import org.intellij.lang.annotations.*
import kotlin.io.path.*
import kotlin.test.*

// Verifies @Service / @Provides FIR checkers fire on multiplatform COMMON compilation,
// not just on the JVM single-target path (covered by CheckerTest).
class CheckerKmpTest : TestProjectBasedTest() {

    private fun bootstrapCheckerKmpProject(@Language("kotlin") code: String) = context(project) {
        bootstrapKotlinMultiplatformProject()
        file("build.gradle.kts").appendText(
            """
            kotlin { jvm() }
            """.trimIndent()
        )
        file("src/commonMain/kotlin/Subject.kt").kotlin(
            """
            |import dev.whyoleg.sweetspi.*
            |
            |${code.alignMargin()}
            |
            """.trimMargin()
        )
    }

    private fun assertCompilationError(
        @Language("kotlin") code: String,
        expectedMessage: String,
    ) = context(project) {
        bootstrapCheckerKmpProject(code)
        gradle("compileKotlinJvm", expectFailure = true) {
            assertTrue(
                output.contains(expectedMessage),
                "expected diagnostic message <$expectedMessage> in build output, but was:\n$output"
            )
        }
    }

    private fun assertCompiles(
        @Language("kotlin") code: String,
    ) = context(project) {
        bootstrapCheckerKmpProject(code)
        gradle("compileKotlinJvm") {
            assertTrue(task(":compileKotlinJvm")!!.outcome.isPositive)
        }
    }

    @Test
    fun serviceOnFinalClass() = assertCompilationError(
        code = "@Service class NotAService",
        expectedMessage = "@Service is only applicable to interfaces or abstract classes"
    )

    @Test
    fun providesAndServiceTogether() = assertCompilationError(
        code = """
        @Service interface S
        @Service @Provides(S::class) interface Both : S
        """.trimIndent(),
        expectedMessage = "both @Service and @Provides"
    )

    @Test
    fun providesTypeMismatch() = assertCompilationError(
        code = """
        @Service interface S
        class Unrelated
        @Provides(S::class) object Impl : Unrelated()
        """.trimIndent(),
        expectedMessage = "not a subtype of service"
    )

    @Test
    fun providesAbstractClass() = assertCompilationError(
        code = """
        @Service interface S
        @Provides(S::class) abstract class Impl : S
        """.trimIndent(),
        expectedMessage = "must be an object or an instantiable class"
    )

    @Test
    fun providesFunctionInClassRejected() = assertCompilationError(
        code = """
        @Service interface S
        class Impl : S
        class Holder { @Provides(S::class) fun provide(): S = Impl() }
        """.trimIndent(),
        expectedMessage = "@Provides function/property must be top-level or a member of an object"
    )

    @Test
    fun validObjectProviderCompiles() = assertCompiles(
        code = """
        @Service interface S
        @Provides(S::class) object Impl : S
        """.trimIndent()
    )
}
```

- [ ] **Step 2: Run the tests to verify they pass**

Run: `./gradlew :sweetspi-tests:test --tests "dev.whyoleg.sweetspi.tests.CheckerKmpTest"`
Expected: PASS — every `assertCompilationError` test sees its message in the failed inner build, and
`validObjectProviderCompiles` compiles cleanly.

If an `assertCompilationError` test reports the inner build *succeeded* (the common diagnostic did not
surface on `compileKotlinJvm`), switch the compile task in `assertCompilationError` / `assertCompiles`
to `compileCommonMainKotlinMetadata` (the dedicated common-metadata compilation), re-run, and keep
whichever task reliably triggers the `MppCheckerKind.Common` checkers. Verify the exact task name via
the IDEA MCP server if unsure — do not guess silently.

- [ ] **Step 3: Commit**

```bash
git add sweetspi-tests/src/test/kotlin/CheckerKmpTest.kt
git commit -m "test: run @Service/@Provides checkers on KMP common compilation"
```

---

## Task 5: Full M0 regression run

**Files:** none (verification only).

- [ ] **Step 1: Run the new M0 tests together**

Run:
```bash
./gradlew :sweetspi-tests:test \
  --tests "dev.whyoleg.sweetspi.tests.MultiModuleServiceTest" \
  --tests "dev.whyoleg.sweetspi.tests.CheckerKmpTest"
```
Expected: BUILD SUCCESSFUL.

- [ ] **Step 2: Confirm pre-existing tests still pass (incl. full multiplatform)**

Run: `./gradlew :sweetspi-tests:test`
Expected: BUILD SUCCESSFUL (includes `CheckerTest`, `SimpleServiceTest` across all targets, and the new
M0 tests — this is the real proof the Task 1 bootstrap refactor is behavior-preserving). If
`SimpleServiceTest.testMultiplatform` exhibits the documented `:updateKotlinAbi :check` flakiness, that
is pre-existing and unrelated to M0 — note it, do not attempt to fix it here.

- [ ] **Step 3: No commit needed** (verification task). These tests must remain **green and unchanged**
  through M1 — that invariance is the proof M1's refactor preserves behavior.

---

## Self-review

**Spec coverage (M0 in `2026-06-02-roadmap-design.md`):**
- Multi-module, ≥3 modules, `@Service`/`@Provides`/consumer split → Task 2 (JVM) + Task 3 (KMP). ✅
- JVM **and** ≥1 klib target → Task 2 (JVM) + Task 3 (jvm + macosArm64). ✅
- Transitive-dependency "library" variant → both tests: `:consumer` depends only on `:provider`,
  service type reaches it via `api(project(":service"))`. ✅
- Multi-module support in `TestProject` infra → Task 1 Step 2. ✅
- KMP checker tests → Task 4. ✅
- Exit: new tests green + must survive M1 unchanged → Task 5 + explicit invariance note. ✅

**Reviewer constraints honored:** no JVM-vs-MPP axis abstraction (separate `jvmModule` /
`multiplatformModule`); helpers hide only build-file logic; tests keep explicit `file("…/src/…")`
source placement and explicit `gradle(":consumer:…")` task calls; helpers reused by the existing
bootstraps (Task 1 Step 3). ✅

**Placeholder scan:** no TBD/TODO-as-instruction; every code step shows full content; run commands and
expected outcomes concrete. The two conditional branches (native flakiness in Task 3; compile-task
choice in Task 4) are explicit decision branches with named alternatives, not placeholders. ✅

**Type/name consistency:** `jvmModule(path, abiValidation, dependencies)` and
`multiplatformModule(path, targets, abiValidation, commonMainDependencies, commonTestDependencies)`
are defined in Task 1 Step 1 and called with those exact parameter names in Tasks 1–3.
`bootstrapMultiModuleProject(modules: List<String>)` defined in Task 1 Step 2, called in Tasks 2–3.
Module dirs `service`/`provider`/`consumer`, packages `sweettests.{service,provider,consumer}`, and
the `GreetingService.greet()` / `EnglishGreeting` symbols are consistent across Tasks 2–3. Checker
messages in Task 4 are copied verbatim from `fir/SweetDiagnostics.kt` renderer strings. ✅
```

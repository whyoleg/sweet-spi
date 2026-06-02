# M0 — Regression Safety Net Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Lock current cross-module SPI discovery and checker behavior behind tests so the M1 FIR/IR refactor can be proven behavior-preserving.

**Architecture:** Add multi-module support to the existing Gradle-TestKit-based test harness, then add (a) JVM and (b) Kotlin-Multiplatform functional tests where a `@Service` lives in one module, its `@Provides` implementation in a second, and the consuming `ServiceLoader.load` call in a third — with the service type reaching the consumer only *transitively* (the "library" promise). Finally, add a KMP variant of the checker tests to prove the FIR diagnostics fire on common-code compilation, not just JVM.

**Tech Stack:** Kotlin 2.3.20, Gradle TestKit (`GradleRunner`), JUnit5 (`kotlin("test-junit5")` + `useJUnitPlatform()`), context parameters (`-Xcontext-parameters`).

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
    root project (root `build.gradle.kts` + `gradle.properties` + `gradle/libs.versions.toml` +
    `settings.gradle.kts`). Both enable ABI validation.
  - `file(path): Path` — resolves under the test project dir, creating parent dirs.
  - `Path.kts(code)` — write a `.gradle.kts` file verbatim.
  - `Path.kotlin(packageName, code)` — write a `.kt` file prefixed with `package $packageName`.
  - `Path.kotlin(code)` — same but package defaults to `sweettests.<testMethodName>`.
  - `gradle(vararg args, expectFailure = false, block)` — run the inner build; `block` receives
    `BuildResult` (use `.output`, `.task(":x")!!.outcome.isPositive`).
  - `String.alignMargin(prefix = "|")` — re-indent multi-line snippets.
- The version catalog written by the harness defines aliases `libs.plugins.kotlin.jvm`,
  `libs.plugins.kotlin.multiplatform`, `libs.plugins.sweetspi`. The `libs` catalog is available in
  **every** module build file of the spawned project.
- **`SweetSpiPlugin` auto-adds the runtime dependency** to `main` (JVM) / `commonMain` (KMP) when
  applied — module build files must NOT add the runtime manually.
- **Decision — no ABI validation in the multi-module test modules.** The single-module bootstraps
  enable it (and require `:updateKotlinAbi`), and there is a known flaky interaction documented in
  `SimpleServiceTest`. M0's multi-module tests are pure execution tests; we write per-module build
  files by hand and omit `abiValidation` to keep them robust and fast.
- **Cross-module SPI flow being pinned:** the `$ServiceProvider` nested interface is generated in the
  module that holds `@Service`; the provider-impl class + `META-INF/services` entry (JVM) / eager-init
  registration (klib) are generated in the module that holds `@Provides`; the consumer discovers them
  at runtime. The transitive-library variant makes the consumer depend only on the *provider* module,
  receiving the service type via the provider's `api(project(":service"))`.

---

## File structure

| File | Responsibility | Change |
|------|----------------|--------|
| `sweetspi-tests/src/test/kotlin/api/TestProject.kt` | harness helpers | **modify**: add `includes` param to `bootstrapGradleProject`; add `bootstrapMultiModuleProject` |
| `sweetspi-tests/src/test/kotlin/MultiModuleServiceTest.kt` | JVM + KMP cross-module execution tests | **create** |
| `sweetspi-tests/src/test/kotlin/CheckerKmpTest.kt` | checker diagnostics on KMP common compilation | **create** |

---

## Task 1: Multi-module harness support + JVM cross-module execution test

**Files:**
- Modify: `sweetspi-tests/src/test/kotlin/api/TestProject.kt`
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

class MultiModuleServiceTest : TestProjectBasedTest() {

    // service in :service, provider in :provider, consumer in :consumer.
    // :consumer depends only on :provider; the service type reaches it transitively
    // via :provider's `api(project(":service"))` — the "library" promise.
    @Test
    fun testJvm() = context(project) {
        bootstrapMultiModuleProject(listOf("service", "provider", "consumer"))

        file("service/build.gradle.kts").kts(
            """
            plugins {
                alias(libs.plugins.kotlin.jvm)
                alias(libs.plugins.sweetspi)
            }
            """.trimIndent()
        )
        file("service/src/main/kotlin/GreetingService.kt").kotlin(
            "sweettests.service",
            """
            import dev.whyoleg.sweetspi.*
            @Service interface GreetingService { fun greet(): String }
            """.trimIndent()
        )

        file("provider/build.gradle.kts").kts(
            """
            plugins {
                alias(libs.plugins.kotlin.jvm)
                alias(libs.plugins.sweetspi)
            }
            dependencies { api(project(":service")) }
            """.trimIndent()
        )
        file("provider/src/main/kotlin/EnglishGreeting.kt").kotlin(
            "sweettests.provider",
            """
            import dev.whyoleg.sweetspi.*
            import sweettests.service.*
            @Provides(GreetingService::class) object EnglishGreeting : GreetingService {
                override fun greet(): String = "hello"
            }
            """.trimIndent()
        )

        file("consumer/build.gradle.kts").kts(
            """
            plugins {
                alias(libs.plugins.kotlin.jvm)
                alias(libs.plugins.sweetspi)
            }
            dependencies {
                implementation(project(":provider"))
                implementation(kotlin("test"))
            }
            """.trimIndent()
        )
        file("consumer/src/test/kotlin/ConsumerTest.kt").kotlin(
            "sweettests.consumer",
            """
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
        )

        gradle(":consumer:test") {
            assertTrue(task(":consumer:test")!!.outcome.isPositive)
        }
    }
}
```

- [ ] **Step 2: Run the test to verify it fails to compile**

Run: `./gradlew :sweetspi-tests:compileTestKotlin`
Expected: FAIL — unresolved reference `bootstrapMultiModuleProject`.

- [ ] **Step 3: Add multi-module harness support**

In `sweetspi-tests/src/test/kotlin/api/TestProject.kt`, change the `bootstrapGradleProject`
signature to accept module includes and emit them into `settings.gradle.kts`. Replace the current
private function (lines ~96-150) with this version — note the only behavioral change is the new
`includes` parameter and the appended `include(...)` lines:

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

Then add the public multi-module bootstrap next to the other bootstrap functions (e.g. right after
`bootstrapKotlinMultiplatformProject`):

```kotlin
// Multi-module project: writes shared settings (with `include(...)`), properties and version
// catalog. The caller writes each module's `build.gradle.kts` + sources via `file(...)`.
// No root `build.gradle.kts` is written and ABI validation is intentionally not enabled.
context(project: TestProject)
fun bootstrapMultiModuleProject(modules: List<String>) {
    bootstrapGradleProject(includes = modules)
}
```

- [ ] **Step 4: Run the test to verify it passes**

Run: `./gradlew :sweetspi-tests:test --tests "dev.whyoleg.sweetspi.tests.MultiModuleServiceTest.testJvm"`
Expected: PASS. (Cross-module discovery already works; this test pins that behavior.)

- [ ] **Step 5: Commit**

```bash
git add sweetspi-tests/src/test/kotlin/api/TestProject.kt sweetspi-tests/src/test/kotlin/MultiModuleServiceTest.kt
git commit -m "test: pin JVM cross-module SPI discovery (transitive library)"
```

---

## Task 2: KMP cross-module execution test (jvm + macosArm64)

**Files:**
- Modify: `sweetspi-tests/src/test/kotlin/MultiModuleServiceTest.kt`

- [ ] **Step 1: Add the failing KMP test**

Append this method inside `MultiModuleServiceTest`:

```kotlin
    @Test
    fun testMultiplatform() = context(project) {
        bootstrapMultiModuleProject(listOf("service", "provider", "consumer"))

        file("service/build.gradle.kts").kts(
            """
            plugins {
                alias(libs.plugins.kotlin.multiplatform)
                alias(libs.plugins.sweetspi)
            }
            kotlin { jvm(); macosArm64() }
            """.trimIndent()
        )
        file("service/src/commonMain/kotlin/GreetingService.kt").kotlin(
            "sweettests.service",
            """
            import dev.whyoleg.sweetspi.*
            @Service interface GreetingService { fun greet(): String }
            """.trimIndent()
        )

        file("provider/build.gradle.kts").kts(
            """
            plugins {
                alias(libs.plugins.kotlin.multiplatform)
                alias(libs.plugins.sweetspi)
            }
            kotlin {
                jvm(); macosArm64()
                sourceSets.commonMain.dependencies { api(project(":service")) }
            }
            """.trimIndent()
        )
        file("provider/src/commonMain/kotlin/EnglishGreeting.kt").kotlin(
            "sweettests.provider",
            """
            import dev.whyoleg.sweetspi.*
            import sweettests.service.*
            @Provides(GreetingService::class) object EnglishGreeting : GreetingService {
                override fun greet(): String = "hello"
            }
            """.trimIndent()
        )

        file("consumer/build.gradle.kts").kts(
            """
            plugins {
                alias(libs.plugins.kotlin.multiplatform)
                alias(libs.plugins.sweetspi)
            }
            kotlin {
                jvm(); macosArm64()
                sourceSets.commonMain.dependencies { implementation(project(":provider")) }
                sourceSets.commonTest.dependencies { implementation(kotlin("test")) }
            }
            """.trimIndent()
        )
        file("consumer/src/commonTest/kotlin/ConsumerTest.kt").kotlin(
            "sweettests.consumer",
            """
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
        )

        gradle(":consumer:jvmTest", ":consumer:macosArm64Test") {
            assertTrue(task(":consumer:jvmTest")!!.outcome.isPositive)
            assertTrue(task(":consumer:macosArm64Test")!!.outcome.isPositive)
        }
    }
```

- [ ] **Step 2: Run the test to verify it fails first (expected red), then passes**

Run: `./gradlew :sweetspi-tests:test --tests "dev.whyoleg.sweetspi.tests.MultiModuleServiceTest.testMultiplatform"`
Expected: PASS. (Pins cross-module klib eager-init registration + JVM discovery.)

If `:consumer:macosArm64Test` fails for a reason **unrelated** to providers (toolchain/linker
flakiness), do NOT mask it — capture the failure output and report it as an M0 finding per the spec.
If it fails because providers are *not discovered cross-module on native*, that is exactly the kind
of behavior M0 exists to surface — report it before proceeding to M1.

- [ ] **Step 3: Commit**

```bash
git add sweetspi-tests/src/test/kotlin/MultiModuleServiceTest.kt
git commit -m "test: pin KMP cross-module SPI discovery (jvm + macosArm64)"
```

---

## Task 3: KMP checker tests (diagnostics on common compilation)

**Files:**
- Create: `sweetspi-tests/src/test/kotlin/CheckerKmpTest.kt`

**Rationale for a representative subset:** the FIR checkers are registered with
`MppCheckerKind.Common` (see `fir/SweetFirExtensionRegistrar.kt` / `ProvidesChecker`), so the *same*
code path runs for JVM and KMP. The goal here is to prove the checkers actually fire during
multiplatform **common** compilation, not to re-assert every rule. We cover one `@Service` rule, the
service/provides cross-check, two `@Provides`-on-class rules, the callable-placement rule, and one
positive control.

- [ ] **Step 1: Write the failing tests**

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
// not just on the JVM target (CheckerTest covers the JVM single-target path).
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

- [ ] **Step 2: Run the error tests to verify they fail the build as expected**

Run: `./gradlew :sweetspi-tests:test --tests "dev.whyoleg.sweetspi.tests.CheckerKmpTest"`
Expected: PASS — every `assertCompilationError` test sees its message in the failed inner build, and
`validObjectProviderCompiles` compiles cleanly.

If `compileKotlinJvm` does NOT surface a common-code diagnostic (i.e. an `assertCompilationError`
test reports the inner build *succeeded*), switch the compile task in `assertCompilationError` /
`assertCompiles` to `compileCommonMainKotlinMetadata` (the dedicated common-metadata compilation),
re-run, and keep whichever task reliably triggers the `MppCheckerKind.Common` checkers. Verify the
exact task name via the IDEA MCP server if unsure — do not guess silently.

- [ ] **Step 3: Commit**

```bash
git add sweetspi-tests/src/test/kotlin/CheckerKmpTest.kt
git commit -m "test: run @Service/@Provides checkers on KMP common compilation"
```

---

## Task 4: Full M0 regression run

**Files:** none (verification only).

- [ ] **Step 1: Run the new M0 tests together**

Run:
```bash
./gradlew :sweetspi-tests:test \
  --tests "dev.whyoleg.sweetspi.tests.MultiModuleServiceTest" \
  --tests "dev.whyoleg.sweetspi.tests.CheckerKmpTest"
```
Expected: BUILD SUCCESSFUL.

- [ ] **Step 2: Confirm pre-existing tests still pass**

Run: `./gradlew :sweetspi-tests:test`
Expected: BUILD SUCCESSFUL (includes `CheckerTest`, `SimpleServiceTest`, and the new M0 tests).
If `SimpleServiceTest.testMultiplatform` exhibits the documented `:updateKotlinAbi :check` flakiness,
that is pre-existing and unrelated to M0 — note it, do not attempt to fix it here.

- [ ] **Step 3: No commit needed** (verification task). These tests must remain **green and unchanged**
  through M1 — that invariance is the proof M1's refactor preserves behavior.

---

## Self-review

**Spec coverage (M0 in `2026-06-02-roadmap-design.md`):**
- Multi-module, ≥3 modules, `@Service`/`@Provides`/consumer split → Task 1 (JVM) + Task 2 (KMP). ✅
- JVM **and** ≥1 klib target → Task 1 (JVM) + Task 2 (jvm + macosArm64). ✅
- Transitive-dependency "library" variant → both tests: `:consumer` depends only on `:provider`,
  service type reaches it via `api(project(":service"))`. ✅
- Multi-module support in `TestProject` infra → Task 1 Step 3. ✅
- KMP checker tests (checkers run on multiplatform project) → Task 3. ✅
- Exit: new tests green + must survive M1 unchanged → Task 4 + explicit invariance note. ✅

**Placeholder scan:** no TBD/TODO-as-instruction; every code step shows full content; run commands and
expected outcomes are concrete. The two conditional fallbacks (native flakiness in Task 2; compile-task
choice in Task 3) are explicit decision branches with named alternatives, not placeholders. ✅

**Type/name consistency:** `bootstrapMultiModuleProject(modules: List<String>)` defined in Task 1
Step 3, called identically in Tasks 1 & 2. Module names `service`/`provider`/`consumer`, package names
`sweettests.{service,provider,consumer}`, and the `GreetingService.greet()` / `EnglishGreeting` symbols
are consistent across Tasks 1–2. Checker messages in Task 3 are copied verbatim from
`fir/SweetDiagnostics.kt` renderer strings. ✅

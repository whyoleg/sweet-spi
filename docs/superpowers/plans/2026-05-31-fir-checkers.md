# SweetSPI FIR Checkers Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add K2 FIR checkers that reject misuse of `@Service` / `@Provides` as compilation errors, with integration tests asserting the diagnostic messages appear in the build output.

**Architecture:** A `KtDiagnosticsContainer` declares error factories + a renderer map. Four `FirDeclarationChecker`s (one for `@Service` classes, three for `@Provides` on class/function/property) report those diagnostics. They are wired into the already-registered `SweetFirCheckers` extension. Shared annotation/subtype/visibility logic lives in one utils file. Tests compile bad Kotlin through the real plugin (Gradle TestKit) and assert `buildAndFail()` output contains each message.

**Tech Stack:** Kotlin 2.3.20 compiler plugin API (`compiler-embeddable`), JUnit5 + Gradle TestKit.

---

## Verified API facts (Kotlin 2.3.20, from `compiler-embeddable` sources)

- Checker base: `FirRegularClassChecker = FirDeclarationChecker<FirRegularClass>`, `FirSimpleFunctionChecker = FirDeclarationChecker<FirNamedFunction>`, `FirPropertyChecker = FirDeclarationChecker<FirProperty>`. Construct with `MppCheckerKind`. The `check` method uses **context parameters**:
  ```kotlin
  context(context: CheckerContext, reporter: DiagnosticReporter)
  override fun check(declaration: FirRegularClass) { ... }
  ```
- Reporting: `reporter.reportOn(source, FACTORY)` / `reporter.reportOn(source, FACTORY1, arg)` (import `org.jetbrains.kotlin.diagnostics.reportOn`); `context` is implicit via context parameters.
- Diagnostics DSL (context of a `KtDiagnosticsContainer`): `val X by error0<PsiElement>()`, `val Y by error1<PsiElement, String>()`.
- Renderer map: public factory `KtDiagnosticFactoryToRendererMap(name) { map -> map.put(...) }` returns `Lazy<…>`; use it as the delegate for `override val MAP`.
- `CommonRenderers.STRING : DiagnosticParameterRenderer<String>`.
- Annotations: `FirAnnotationContainer.getAnnotationsByClassId(classId, session): List<FirAnnotation>`, `FirAnnotation.getKClassArgument(name, session): ConeKotlinType?`, `FirBasedSymbol.hasAnnotation(classId, session): Boolean`.
- Types/super: `ConeKotlinType.toRegularClassSymbol(session): FirRegularClassSymbol?`; `lookupSuperTypes(symbols: List<FirClassSymbol<*>>, lookupInterfaces, deep, useSiteSession, substituteTypes): List<ConeClassLikeType>`; `ConeClassLikeType.lookupTag.classId`.
- Status/visibility: `declaration.status.visibility` (`org.jetbrains.kotlin.descriptors.Visibilities.Public/Internal/Local`), `declaration.status.isInner`, `declaration.classKind`, `declaration.status.modality`. `ClassId.isLocal`.

If any extension above resolves differently at compile time, confirm via the IDEA MCP server (`search_symbol` / `read_file` into the sources jar) — do **not** unzip jars.

## File structure

```
sweetspi-compiler-plugin/src/main/kotlin/
  SweetNames.kt                       # MODIFY: add ProvidesServiceArgument Name
  SweetFirCheckers.kt                 # MODIFY: override declarationCheckers with the 4 checkers
  checkers/SweetDiagnostics.kt        # CREATE: container + renderer
  checkers/SweetCheckerUtils.kt       # CREATE: shared helpers
  checkers/ServiceChecker.kt          # CREATE
  checkers/ProvidesClassChecker.kt    # CREATE
  checkers/ProvidesFunctionChecker.kt # CREATE
  checkers/ProvidesPropertyChecker.kt # CREATE
sweetspi-tests/src/test/kotlin/
  CheckerTest.kt                      # CREATE
```

## Test execution notes

- Run a single checker test:
  `./gradlew :sweetspi-tests:test --tests "dev.whyoleg.sweetspi.tests.CheckerTest.<method>"`
- The test task resolves `devArtifacts` from `:sweetspi-compiler-plugin` (and runtime/gradle-plugin), so it **rebuilds & re-publishes the plugin automatically**. The first run after a plugin change is slow.
- Error tests use `gradle("compileKotlin", expectFailure = true)` — IR generation never runs when FIR reports an error, so no resource paths are needed.

---

## Task 1: Scaffolding — names, diagnostics, utils, empty checkers, wiring

**Files:**
- Modify: `sweetspi-compiler-plugin/src/main/kotlin/SweetNames.kt`
- Create: `sweetspi-compiler-plugin/src/main/kotlin/checkers/SweetDiagnostics.kt`
- Create: `sweetspi-compiler-plugin/src/main/kotlin/checkers/SweetCheckerUtils.kt`
- Create: `sweetspi-compiler-plugin/src/main/kotlin/checkers/ServiceChecker.kt`
- Create: `sweetspi-compiler-plugin/src/main/kotlin/checkers/ProvidesClassChecker.kt`
- Create: `sweetspi-compiler-plugin/src/main/kotlin/checkers/ProvidesFunctionChecker.kt`
- Create: `sweetspi-compiler-plugin/src/main/kotlin/checkers/ProvidesPropertyChecker.kt`
- Modify: `sweetspi-compiler-plugin/src/main/kotlin/SweetFirCheckers.kt`

This task produces a compiling plugin with all 15 diagnostics declared, helpers implemented, and four checker objects that compile but report nothing yet (logic added in Tasks 2–5). No checker test here — it is pure infrastructure verified by compiling.

- [ ] **Step 1: Add the `@Provides(service=…)` argument name to `SweetNames`**

In `SweetNames.kt`, after `val Provides = classId("Provides")`, add:

```kotlin
    // name of the `service` argument of `@Provides(service = ...)`
    val ProvidesServiceArgument = Name.identifier("service")
```

(`Name` is already imported via `org.jetbrains.kotlin.name.*`.)

- [ ] **Step 2: Create `checkers/SweetDiagnostics.kt`**

```kotlin
/*
 * Copyright (c) 2026 Oleg Yukhnevich. Use of this source code is governed by the Apache 2.0 license.
 */

package dev.whyoleg.sweetspi.compiler.checkers

import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.diagnostics.KtDiagnosticFactoryToRendererMap
import org.jetbrains.kotlin.diagnostics.KtDiagnosticsContainer
import org.jetbrains.kotlin.diagnostics.error0
import org.jetbrains.kotlin.diagnostics.error1
import org.jetbrains.kotlin.diagnostics.rendering.BaseDiagnosticRendererFactory
import org.jetbrains.kotlin.diagnostics.rendering.CommonRenderers

internal object SweetDiagnostics : KtDiagnosticsContainer() {
    // @Service
    val SERVICE_WRONG_TARGET by error0<PsiElement>()
    val SERVICE_WRONG_VISIBILITY by error0<PsiElement>()

    // @Provides (shared)
    val PROVIDES_AND_SERVICE_TOGETHER by error0<PsiElement>()
    val PROVIDES_SERVICE_NOT_A_SERVICE by error1<PsiElement, String>()
    val PROVIDES_TYPE_MISMATCH by error1<PsiElement, String>()
    val PROVIDES_WRONG_VISIBILITY by error0<PsiElement>()

    // @Provides on class
    val PROVIDES_CLASS_NOT_INSTANTIABLE by error0<PsiElement>()
    val PROVIDES_CLASS_INNER by error0<PsiElement>()
    val PROVIDES_CLASS_LOCAL by error0<PsiElement>()
    val PROVIDES_CLASS_NO_CONSTRUCTOR by error0<PsiElement>()

    // @Provides on function
    val PROVIDES_FUNCTION_RECEIVER by error0<PsiElement>()
    val PROVIDES_FUNCTION_SUSPEND by error0<PsiElement>()
    val PROVIDES_FUNCTION_PARAMETERS by error0<PsiElement>()

    // @Provides on property
    val PROVIDES_PROPERTY_MUTABLE by error0<PsiElement>()
    val PROVIDES_PROPERTY_RECEIVER by error0<PsiElement>()

    override fun getRendererFactory(): BaseDiagnosticRendererFactory = SweetDiagnosticRenderers
}

private object SweetDiagnosticRenderers : BaseDiagnosticRendererFactory() {
    override val MAP: KtDiagnosticFactoryToRendererMap by KtDiagnosticFactoryToRendererMap("SweetSPI") { map ->
        map.put(SweetDiagnostics.SERVICE_WRONG_TARGET, "@Service is only applicable to interfaces or abstract classes")
        map.put(SweetDiagnostics.SERVICE_WRONG_VISIBILITY, "@Service declaration must be public or internal")

        map.put(SweetDiagnostics.PROVIDES_AND_SERVICE_TOGETHER, "declaration cannot be annotated with both @Service and @Provides")
        map.put(SweetDiagnostics.PROVIDES_SERVICE_NOT_A_SERVICE, "''{0}'' specified in @Provides is not annotated with @Service", CommonRenderers.STRING)
        map.put(SweetDiagnostics.PROVIDES_TYPE_MISMATCH, "provided type is not a subtype of service ''{0}''", CommonRenderers.STRING)
        map.put(SweetDiagnostics.PROVIDES_WRONG_VISIBILITY, "@Provides declaration must be public or internal")

        map.put(SweetDiagnostics.PROVIDES_CLASS_NOT_INSTANTIABLE, "@Provides class must be an object or an instantiable class (not abstract/sealed/enum/annotation/interface)")
        map.put(SweetDiagnostics.PROVIDES_CLASS_INNER, "@Provides class must not be inner")
        map.put(SweetDiagnostics.PROVIDES_CLASS_LOCAL, "@Provides class must not be local")
        map.put(SweetDiagnostics.PROVIDES_CLASS_NO_CONSTRUCTOR, "@Provides class must have a public or internal constructor with no parameters or all-default parameters")

        map.put(SweetDiagnostics.PROVIDES_FUNCTION_RECEIVER, "@Provides function must not have an extension receiver or context parameters")
        map.put(SweetDiagnostics.PROVIDES_FUNCTION_SUSPEND, "@Provides function must not be suspend")
        map.put(SweetDiagnostics.PROVIDES_FUNCTION_PARAMETERS, "@Provides function parameters must all have default values")

        map.put(SweetDiagnostics.PROVIDES_PROPERTY_MUTABLE, "@Provides property must be a val")
        map.put(SweetDiagnostics.PROVIDES_PROPERTY_RECEIVER, "@Provides property must not have an extension receiver or context parameters")
    }
}
```

- [ ] **Step 3: Create `checkers/SweetCheckerUtils.kt`**

```kotlin
/*
 * Copyright (c) 2026 Oleg Yukhnevich. Use of this source code is governed by the Apache 2.0 license.
 */

package dev.whyoleg.sweetspi.compiler.checkers

import dev.whyoleg.sweetspi.compiler.SweetNames
import org.jetbrains.kotlin.descriptors.Visibilities
import org.jetbrains.kotlin.descriptors.Visibility
import org.jetbrains.kotlin.fir.FirSession
import org.jetbrains.kotlin.fir.declarations.getAnnotationsByClassId
import org.jetbrains.kotlin.fir.declarations.getKClassArgument
import org.jetbrains.kotlin.fir.declarations.hasAnnotation
import org.jetbrains.kotlin.fir.declarations.FirAnnotationContainer
import org.jetbrains.kotlin.fir.expressions.FirAnnotation
import org.jetbrains.kotlin.fir.resolve.lookupSuperTypes
import org.jetbrains.kotlin.fir.resolve.toRegularClassSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirClassSymbol
import org.jetbrains.kotlin.fir.types.ConeKotlinType
import org.jetbrains.kotlin.name.ClassId

internal fun Visibility.isAllowedForSweet(): Boolean =
    this == Visibilities.Public || this == Visibilities.Internal

/** Each `@Provides(service = X::class)` annotation on this declaration, paired with the resolved service type X. */
internal fun FirAnnotationContainer.providesServiceTypes(session: FirSession): List<Pair<FirAnnotation, ConeKotlinType>> =
    getAnnotationsByClassId(SweetNames.Provides, session).mapNotNull { annotation ->
        val serviceType = annotation.getKClassArgument(SweetNames.ProvidesServiceArgument, session) ?: return@mapNotNull null
        annotation to serviceType
    }

/** The service type X resolved to its declaring class symbol, or null if unresolvable. */
internal fun ConeKotlinType.serviceClassSymbol(session: FirSession): FirClassSymbol<*>? =
    toRegularClassSymbol(session)

/** True if X (the service) is itself annotated with @Service. */
internal fun FirClassSymbol<*>.isAnnotatedAsService(session: FirSession): Boolean =
    hasAnnotation(SweetNames.Service, session)

/** True if `this` provider class is a subtype of the service identified by [serviceClassId] (compared by classifier, ignoring type arguments). */
internal fun FirClassSymbol<*>.isSweetSubclassOf(serviceClassId: ClassId, session: FirSession): Boolean {
    if (classId == serviceClassId) return true
    return lookupSuperTypes(
        symbols = listOf(this),
        lookupInterfaces = true,
        deep = true,
        useSiteSession = session,
        substituteTypes = false,
    ).any { it.lookupTag.classId == serviceClassId }
}
```

- [ ] **Step 4: Create the four checker objects as no-op stubs (logic added later)**

`checkers/ServiceChecker.kt`:

```kotlin
/*
 * Copyright (c) 2026 Oleg Yukhnevich. Use of this source code is governed by the Apache 2.0 license.
 */

package dev.whyoleg.sweetspi.compiler.checkers

import org.jetbrains.kotlin.diagnostics.DiagnosticReporter
import org.jetbrains.kotlin.fir.analysis.checkers.MppCheckerKind
import org.jetbrains.kotlin.fir.analysis.checkers.context.CheckerContext
import org.jetbrains.kotlin.fir.analysis.checkers.declaration.FirRegularClassChecker
import org.jetbrains.kotlin.fir.declarations.FirRegularClass

internal object ServiceChecker : FirRegularClassChecker(MppCheckerKind.Common) {
    context(context: CheckerContext, reporter: DiagnosticReporter)
    override fun check(declaration: FirRegularClass) {
        // logic added in Task 2
    }
}
```

`checkers/ProvidesClassChecker.kt`:

```kotlin
/*
 * Copyright (c) 2026 Oleg Yukhnevich. Use of this source code is governed by the Apache 2.0 license.
 */

package dev.whyoleg.sweetspi.compiler.checkers

import org.jetbrains.kotlin.diagnostics.DiagnosticReporter
import org.jetbrains.kotlin.fir.analysis.checkers.MppCheckerKind
import org.jetbrains.kotlin.fir.analysis.checkers.context.CheckerContext
import org.jetbrains.kotlin.fir.analysis.checkers.declaration.FirRegularClassChecker
import org.jetbrains.kotlin.fir.declarations.FirRegularClass

internal object ProvidesClassChecker : FirRegularClassChecker(MppCheckerKind.Common) {
    context(context: CheckerContext, reporter: DiagnosticReporter)
    override fun check(declaration: FirRegularClass) {
        // logic added in Task 3
    }
}
```

`checkers/ProvidesFunctionChecker.kt`:

```kotlin
/*
 * Copyright (c) 2026 Oleg Yukhnevich. Use of this source code is governed by the Apache 2.0 license.
 */

package dev.whyoleg.sweetspi.compiler.checkers

import org.jetbrains.kotlin.diagnostics.DiagnosticReporter
import org.jetbrains.kotlin.fir.analysis.checkers.MppCheckerKind
import org.jetbrains.kotlin.fir.analysis.checkers.context.CheckerContext
import org.jetbrains.kotlin.fir.analysis.checkers.declaration.FirSimpleFunctionChecker
import org.jetbrains.kotlin.fir.declarations.FirNamedFunction

internal object ProvidesFunctionChecker : FirSimpleFunctionChecker(MppCheckerKind.Common) {
    context(context: CheckerContext, reporter: DiagnosticReporter)
    override fun check(declaration: FirNamedFunction) {
        // logic added in Task 4
    }
}
```

`checkers/ProvidesPropertyChecker.kt`:

```kotlin
/*
 * Copyright (c) 2026 Oleg Yukhnevich. Use of this source code is governed by the Apache 2.0 license.
 */

package dev.whyoleg.sweetspi.compiler.checkers

import org.jetbrains.kotlin.diagnostics.DiagnosticReporter
import org.jetbrains.kotlin.fir.analysis.checkers.MppCheckerKind
import org.jetbrains.kotlin.fir.analysis.checkers.context.CheckerContext
import org.jetbrains.kotlin.fir.analysis.checkers.declaration.FirPropertyChecker
import org.jetbrains.kotlin.fir.declarations.FirProperty

internal object ProvidesPropertyChecker : FirPropertyChecker(MppCheckerKind.Common) {
    context(context: CheckerContext, reporter: DiagnosticReporter)
    override fun check(declaration: FirProperty) {
        // logic added in Task 5
    }
}
```

- [ ] **Step 5: Wire checkers into `SweetFirCheckers.kt`**

Replace the whole file body with:

```kotlin
/*
 * Copyright (c) 2025 Oleg Yukhnevich. Use of this source code is governed by the Apache 2.0 license.
 */

package dev.whyoleg.sweetspi.compiler

import dev.whyoleg.sweetspi.compiler.checkers.*
import org.jetbrains.kotlin.fir.*
import org.jetbrains.kotlin.fir.analysis.checkers.declaration.*
import org.jetbrains.kotlin.fir.analysis.extensions.*

// checkers that annotations are applied to correct functions/properties/classes
class SweetFirCheckers(session: FirSession) : FirAdditionalCheckersExtension(session) {
    override val declarationCheckers: DeclarationCheckers = object : DeclarationCheckers() {
        override val regularClassCheckers: Set<FirRegularClassChecker> = setOf(ServiceChecker, ProvidesClassChecker)
        override val simpleFunctionCheckers: Set<FirSimpleFunctionChecker> = setOf(ProvidesFunctionChecker)
        override val propertyCheckers: Set<FirPropertyChecker> = setOf(ProvidesPropertyChecker)
    }
}
```

- [ ] **Step 6: Compile the plugin to verify scaffolding**

Run: `./gradlew :sweetspi-compiler-plugin:compileKotlin`
Expected: BUILD SUCCESSFUL. If an extension import fails to resolve, locate the correct symbol via IDEA MCP `search_symbol` and fix the import, then re-run.

- [ ] **Step 7: Commit**

```bash
git add sweetspi-compiler-plugin/src/main/kotlin/SweetNames.kt \
        sweetspi-compiler-plugin/src/main/kotlin/SweetFirCheckers.kt \
        sweetspi-compiler-plugin/src/main/kotlin/checkers/
git commit -m "feat(checkers): scaffold FIR diagnostics, utils, and checker wiring"
```

---

## Task 2: ServiceChecker logic + tests

**Files:**
- Modify: `sweetspi-compiler-plugin/src/main/kotlin/checkers/ServiceChecker.kt`
- Create: `sweetspi-tests/src/test/kotlin/CheckerTest.kt`

- [ ] **Step 1: Write `CheckerTest.kt` with the helper and the two `@Service` failing tests**

```kotlin
/*
 * Copyright (c) 2026 Oleg Yukhnevich. Use of this source code is governed by the Apache 2.0 license.
 */

package dev.whyoleg.sweetspi.tests

import dev.whyoleg.sweetspi.tests.api.*
import org.intellij.lang.annotations.*
import kotlin.test.*

class CheckerTest : TestProjectBasedTest() {

    private fun assertCompilationError(
        @Language("kotlin") code: String,
        expectedMessage: String,
    ) = context(project) {
        bootstrapKotlinJvmProject()
        file("src/main/kotlin/Subject.kt").kotlin(
            """
            |import dev.whyoleg.sweetspi.*
            |
            |${code.alignMargin()}
            |
            """.trimMargin()
        )
        gradle("compileKotlin", expectFailure = true) {
            assertTrue(
                output.contains(expectedMessage),
                "expected diagnostic message <$expectedMessage> in build output, but was:\n$output"
            )
        }
    }

    private fun assertCompiles(
        @Language("kotlin") code: String,
    ) = context(project) {
        bootstrapKotlinJvmProject()
        file("src/main/kotlin/Subject.kt").kotlin(
            """
            |import dev.whyoleg.sweetspi.*
            |
            |${code.alignMargin()}
            |
            """.trimMargin()
        )
        gradle("compileKotlin") {
            assertTrue(task(":compileKotlin")!!.outcome.isPositive)
        }
    }

    @Test
    fun serviceOnFinalClass() = assertCompilationError(
        code = "@Service class NotAService",
        expectedMessage = "@Service is only applicable to interfaces or abstract classes"
    )

    @Test
    fun serviceWithPrivateVisibility() = assertCompilationError(
        code = "@Service private interface SecretService",
        expectedMessage = "@Service declaration must be public or internal"
    )
}
```

- [ ] **Step 2: Run the new tests to verify they fail**

Run: `./gradlew :sweetspi-tests:test --tests "dev.whyoleg.sweetspi.tests.CheckerTest.serviceOnFinalClass" --tests "dev.whyoleg.sweetspi.tests.CheckerTest.serviceWithPrivateVisibility"`
Expected: FAIL. The bad code currently compiles, so `buildAndFail()` throws ("build did not fail") → tests fail.

- [ ] **Step 3: Implement ServiceChecker logic**

Replace the `check` body in `ServiceChecker.kt`:

```kotlin
    context(context: CheckerContext, reporter: DiagnosticReporter)
    override fun check(declaration: FirRegularClass) {
        val session = context.session
        if (!declaration.hasAnnotation(SweetNames.Service, session)) return

        val isInterfaceOrAbstractClass = declaration.classKind == ClassKind.INTERFACE ||
                (declaration.classKind == ClassKind.CLASS && declaration.status.modality == Modality.ABSTRACT)
        if (!isInterfaceOrAbstractClass) {
            reporter.reportOn(declaration.source, SweetDiagnostics.SERVICE_WRONG_TARGET)
        }

        if (!declaration.status.visibility.isAllowedForSweet()) {
            reporter.reportOn(declaration.source, SweetDiagnostics.SERVICE_WRONG_VISIBILITY)
        }
    }
```

Add imports to `ServiceChecker.kt`:

```kotlin
import dev.whyoleg.sweetspi.compiler.SweetNames
import org.jetbrains.kotlin.descriptors.ClassKind
import org.jetbrains.kotlin.descriptors.Modality
import org.jetbrains.kotlin.diagnostics.reportOn
import org.jetbrains.kotlin.fir.declarations.hasAnnotation
import org.jetbrains.kotlin.fir.declarations.utils.modality
```

Note: `declaration.status.modality` may be nullable; if the compiler flags it, compare with `== Modality.ABSTRACT` still works on `Modality?`. Use `declaration.modality` (from `declarations.utils`) if `status.modality` is awkward — verify via IDEA MCP if the import fails.

- [ ] **Step 4: Run the two tests to verify they pass**

Run: `./gradlew :sweetspi-tests:test --tests "dev.whyoleg.sweetspi.tests.CheckerTest.serviceOnFinalClass" --tests "dev.whyoleg.sweetspi.tests.CheckerTest.serviceWithPrivateVisibility"`
Expected: PASS (both). The build fails with our message, `buildAndFail()` succeeds, and `output.contains(...)` is true.

- [ ] **Step 5: Commit**

```bash
git add sweetspi-compiler-plugin/src/main/kotlin/checkers/ServiceChecker.kt \
        sweetspi-tests/src/test/kotlin/CheckerTest.kt
git commit -m "feat(checkers): validate @Service target and visibility"
```

---

## Task 3: ProvidesClassChecker logic + tests

**Files:**
- Modify: `sweetspi-compiler-plugin/src/main/kotlin/checkers/ProvidesClassChecker.kt`
- Modify: `sweetspi-tests/src/test/kotlin/CheckerTest.kt`

This checker covers the shared `@Provides` checks (also reused conceptually by Tasks 4–5) plus class-specific ones.

- [ ] **Step 1: Add failing tests to `CheckerTest.kt`**

Append inside the class:

```kotlin
    @Test
    fun providesAndServiceTogether() = assertCompilationError(
        code = """
        @Service interface S
        @Service @Provides(S::class) interface Both : S
        """.trimIndent(),
        expectedMessage = "both @Service and @Provides"
    )

    @Test
    fun providesServiceNotAnnotated() = assertCompilationError(
        code = """
        interface Plain
        @Provides(Plain::class) object Impl : Plain
        """.trimIndent(),
        expectedMessage = "is not annotated with @Service"
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
    fun providesClassWrongVisibility() = assertCompilationError(
        code = """
        @Service interface S
        @Provides(S::class) private object Impl : S
        """.trimIndent(),
        expectedMessage = "@Provides declaration must be public or internal"
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
    fun providesInnerClass() = assertCompilationError(
        code = """
        @Service interface S
        class Outer {
            @Provides(S::class) inner class Impl : S
        }
        """.trimIndent(),
        expectedMessage = "@Provides class must not be inner"
    )

    @Test
    fun providesClassWithRequiredConstructorParam() = assertCompilationError(
        code = """
        @Service interface S
        @Provides(S::class) class Impl(val required: String) : S
        """.trimIndent(),
        expectedMessage = "must have a public or internal constructor"
    )
```

(`PROVIDES_CLASS_LOCAL`: a local class cannot carry a referenced `@Provides` in a way that compiles to a positive case, and inner/visibility cover the practical surface; the diagnostic still exists and is emitted by the logic below. No dedicated test — noted intentionally.)

- [ ] **Step 2: Run these tests to verify they fail**

Run: `./gradlew :sweetspi-tests:test --tests "dev.whyoleg.sweetspi.tests.CheckerTest.providesAndServiceTogether" --tests "dev.whyoleg.sweetspi.tests.CheckerTest.providesServiceNotAnnotated" --tests "dev.whyoleg.sweetspi.tests.CheckerTest.providesTypeMismatch" --tests "dev.whyoleg.sweetspi.tests.CheckerTest.providesClassWrongVisibility" --tests "dev.whyoleg.sweetspi.tests.CheckerTest.providesAbstractClass" --tests "dev.whyoleg.sweetspi.tests.CheckerTest.providesInnerClass" --tests "dev.whyoleg.sweetspi.tests.CheckerTest.providesClassWithRequiredConstructorParam"`
Expected: FAIL (these compile today).

- [ ] **Step 3: Implement ProvidesClassChecker**

Replace `ProvidesClassChecker.kt` entirely:

```kotlin
/*
 * Copyright (c) 2026 Oleg Yukhnevich. Use of this source code is governed by the Apache 2.0 license.
 */

package dev.whyoleg.sweetspi.compiler.checkers

import dev.whyoleg.sweetspi.compiler.SweetNames
import org.jetbrains.kotlin.descriptors.ClassKind
import org.jetbrains.kotlin.descriptors.Modality
import org.jetbrains.kotlin.descriptors.Visibilities
import org.jetbrains.kotlin.diagnostics.DiagnosticReporter
import org.jetbrains.kotlin.diagnostics.reportOn
import org.jetbrains.kotlin.fir.analysis.checkers.MppCheckerKind
import org.jetbrains.kotlin.fir.analysis.checkers.context.CheckerContext
import org.jetbrains.kotlin.fir.analysis.checkers.declaration.FirRegularClassChecker
import org.jetbrains.kotlin.fir.declarations.FirConstructor
import org.jetbrains.kotlin.fir.declarations.FirRegularClass
import org.jetbrains.kotlin.fir.declarations.hasAnnotation
import org.jetbrains.kotlin.fir.declarations.utils.modality

internal object ProvidesClassChecker : FirRegularClassChecker(MppCheckerKind.Common) {
    context(context: CheckerContext, reporter: DiagnosticReporter)
    override fun check(declaration: FirRegularClass) {
        val session = context.session
        if (!declaration.hasAnnotation(SweetNames.Provides, session)) return

        // cross-check: not both @Service and @Provides
        if (declaration.hasAnnotation(SweetNames.Service, session)) {
            reporter.reportOn(declaration.source, SweetDiagnostics.PROVIDES_AND_SERVICE_TOGETHER)
        }

        // visibility
        if (!declaration.status.visibility.isAllowedForSweet()) {
            reporter.reportOn(declaration.source, SweetDiagnostics.PROVIDES_WRONG_VISIBILITY)
        }

        // local / inner
        if (declaration.symbol.classId.isLocal) {
            reporter.reportOn(declaration.source, SweetDiagnostics.PROVIDES_CLASS_LOCAL)
        }
        if (declaration.status.isInner) {
            reporter.reportOn(declaration.source, SweetDiagnostics.PROVIDES_CLASS_INNER)
        }

        // instantiable kind
        val instantiable = when (declaration.classKind) {
            ClassKind.OBJECT -> true
            ClassKind.CLASS -> declaration.modality != Modality.ABSTRACT && declaration.modality != Modality.SEALED
            else -> false // INTERFACE, ENUM_CLASS, ANNOTATION_CLASS
        }
        if (!instantiable) {
            reporter.reportOn(declaration.source, SweetDiagnostics.PROVIDES_CLASS_NOT_INSTANTIABLE)
        } else if (declaration.classKind == ClassKind.CLASS && !hasSuitableConstructor(declaration)) {
            reporter.reportOn(declaration.source, SweetDiagnostics.PROVIDES_CLASS_NO_CONSTRUCTOR)
        }

        // shared @Provides(X) checks: X is a @Service and the class is a subtype of X
        declaration.providesServiceTypes(session).forEach { (annotation, serviceType) ->
            val serviceSymbol = serviceType.serviceClassSymbol(session) ?: return@forEach
            if (!serviceSymbol.isAnnotatedAsService(session)) {
                reporter.reportOn(annotation.source, SweetDiagnostics.PROVIDES_SERVICE_NOT_A_SERVICE, serviceSymbol.classId.asFqNameString())
            } else if (!declaration.symbol.isSweetSubclassOf(serviceSymbol.classId, session)) {
                reporter.reportOn(declaration.source, SweetDiagnostics.PROVIDES_TYPE_MISMATCH, serviceSymbol.classId.asFqNameString())
            }
        }
    }

    private fun hasSuitableConstructor(declaration: FirRegularClass): Boolean {
        val constructors = declaration.declarations.filterIsInstance<FirConstructor>().filter {
            it.status.visibility == Visibilities.Public || it.status.visibility == Visibilities.Internal
        }
        return constructors.any { ctor ->
            ctor.valueParameters.isEmpty() || ctor.valueParameters.all { it.defaultValue != null }
        }
    }
}
```

Notes for the implementer:
- `declaration.symbol.classId.isLocal` — `ClassId.isLocal` exists; verify via IDEA MCP if unresolved.
- `declaration.modality` comes from `org.jetbrains.kotlin.fir.declarations.utils.modality` and is `Modality?`.
- `annotation.source` may be null for synthesized annotations; the test code uses source annotations so it will be present. Reporting on a null source simply produces no location but still emits the message.

- [ ] **Step 4: Run the Task-3 tests to verify they pass**

Run the same command as Step 2.
Expected: PASS (all seven).

- [ ] **Step 5: Commit**

```bash
git add sweetspi-compiler-plugin/src/main/kotlin/checkers/ProvidesClassChecker.kt \
        sweetspi-tests/src/test/kotlin/CheckerTest.kt
git commit -m "feat(checkers): validate @Provides on classes"
```

---

## Task 4: ProvidesFunctionChecker logic + tests

**Files:**
- Modify: `sweetspi-compiler-plugin/src/main/kotlin/checkers/ProvidesFunctionChecker.kt`
- Modify: `sweetspi-tests/src/test/kotlin/CheckerTest.kt`

- [ ] **Step 1: Add failing tests**

Append inside `CheckerTest`:

```kotlin
    @Test
    fun providesSuspendFunction() = assertCompilationError(
        code = """
        @Service interface S
        class Impl : S
        @Provides(S::class) suspend fun provide(): S = Impl()
        """.trimIndent(),
        expectedMessage = "@Provides function must not be suspend"
    )

    @Test
    fun providesFunctionWithReceiver() = assertCompilationError(
        code = """
        @Service interface S
        class Impl : S
        @Provides(S::class) fun String.provide(): S = Impl()
        """.trimIndent(),
        expectedMessage = "must not have an extension receiver or context parameters"
    )

    @Test
    fun providesFunctionWithRequiredParam() = assertCompilationError(
        code = """
        @Service interface S
        class Impl : S
        @Provides(S::class) fun provide(required: String): S = Impl()
        """.trimIndent(),
        expectedMessage = "@Provides function parameters must all have default values"
    )

    @Test
    fun providesFunctionReturnsNonService() = assertCompilationError(
        code = """
        @Service interface S
        class Unrelated
        @Provides(S::class) fun provide(): Unrelated = Unrelated()
        """.trimIndent(),
        expectedMessage = "not a subtype of service"
    )
```

- [ ] **Step 2: Run to verify failure**

Run: `./gradlew :sweetspi-tests:test --tests "dev.whyoleg.sweetspi.tests.CheckerTest.providesSuspendFunction" --tests "dev.whyoleg.sweetspi.tests.CheckerTest.providesFunctionWithReceiver" --tests "dev.whyoleg.sweetspi.tests.CheckerTest.providesFunctionWithRequiredParam" --tests "dev.whyoleg.sweetspi.tests.CheckerTest.providesFunctionReturnsNonService"`
Expected: FAIL.

- [ ] **Step 3: Implement ProvidesFunctionChecker**

Replace `ProvidesFunctionChecker.kt` entirely:

```kotlin
/*
 * Copyright (c) 2026 Oleg Yukhnevich. Use of this source code is governed by the Apache 2.0 license.
 */

package dev.whyoleg.sweetspi.compiler.checkers

import dev.whyoleg.sweetspi.compiler.SweetNames
import org.jetbrains.kotlin.diagnostics.DiagnosticReporter
import org.jetbrains.kotlin.diagnostics.reportOn
import org.jetbrains.kotlin.fir.analysis.checkers.MppCheckerKind
import org.jetbrains.kotlin.fir.analysis.checkers.context.CheckerContext
import org.jetbrains.kotlin.fir.analysis.checkers.declaration.FirSimpleFunctionChecker
import org.jetbrains.kotlin.fir.declarations.FirNamedFunction
import org.jetbrains.kotlin.fir.declarations.hasAnnotation
import org.jetbrains.kotlin.fir.declarations.utils.isSuspend

internal object ProvidesFunctionChecker : FirSimpleFunctionChecker(MppCheckerKind.Common) {
    context(context: CheckerContext, reporter: DiagnosticReporter)
    override fun check(declaration: FirNamedFunction) {
        val session = context.session
        if (!declaration.hasAnnotation(SweetNames.Provides, session)) return

        if (!declaration.status.visibility.isAllowedForSweet()) {
            reporter.reportOn(declaration.source, SweetDiagnostics.PROVIDES_WRONG_VISIBILITY)
        }
        if (declaration.receiverParameter != null || declaration.contextParameters.isNotEmpty()) {
            reporter.reportOn(declaration.source, SweetDiagnostics.PROVIDES_FUNCTION_RECEIVER)
        }
        if (declaration.isSuspend) {
            reporter.reportOn(declaration.source, SweetDiagnostics.PROVIDES_FUNCTION_SUSPEND)
        }
        if (declaration.valueParameters.any { it.defaultValue == null }) {
            reporter.reportOn(declaration.source, SweetDiagnostics.PROVIDES_FUNCTION_PARAMETERS)
        }

        val providedType = declaration.symbol.resolvedReturnType
        val providedSymbol = providedType.serviceClassSymbol(session)
        declaration.providesServiceTypes(session).forEach { (annotation, serviceType) ->
            val serviceSymbol = serviceType.serviceClassSymbol(session) ?: return@forEach
            if (!serviceSymbol.isAnnotatedAsService(session)) {
                reporter.reportOn(annotation.source, SweetDiagnostics.PROVIDES_SERVICE_NOT_A_SERVICE, serviceSymbol.classId.asFqNameString())
            } else if (providedSymbol == null || !providedSymbol.isSweetSubclassOf(serviceSymbol.classId, session)) {
                reporter.reportOn(declaration.source, SweetDiagnostics.PROVIDES_TYPE_MISMATCH, serviceSymbol.classId.asFqNameString())
            }
        }
    }
}
```

Notes:
- `declaration.symbol.resolvedReturnType` returns the function's `ConeKotlinType`.
- `declaration.contextParameters` — verify the exact property name via IDEA MCP if it does not resolve (candidates: `contextParameters`, `contextReceivers`); the receiver-vs-context distinction shares one diagnostic so either covers the test.
- `isSuspend` comes from `org.jetbrains.kotlin.fir.declarations.utils.isSuspend`.

- [ ] **Step 4: Run the Task-4 tests to verify they pass**

Run the same command as Step 2.
Expected: PASS (all four).

- [ ] **Step 5: Commit**

```bash
git add sweetspi-compiler-plugin/src/main/kotlin/checkers/ProvidesFunctionChecker.kt \
        sweetspi-tests/src/test/kotlin/CheckerTest.kt
git commit -m "feat(checkers): validate @Provides on functions"
```

---

## Task 5: ProvidesPropertyChecker logic + tests

**Files:**
- Modify: `sweetspi-compiler-plugin/src/main/kotlin/checkers/ProvidesPropertyChecker.kt`
- Modify: `sweetspi-tests/src/test/kotlin/CheckerTest.kt`

- [ ] **Step 1: Add failing tests**

Append inside `CheckerTest`:

```kotlin
    @Test
    fun providesMutableProperty() = assertCompilationError(
        code = """
        @Service interface S
        class Impl : S
        @Provides(S::class) var provided: S = Impl()
        """.trimIndent(),
        expectedMessage = "@Provides property must be a val"
    )

    @Test
    fun providesPropertyWithReceiver() = assertCompilationError(
        code = """
        @Service interface S
        class Impl : S
        @Provides(S::class) val String.provided: S get() = Impl()
        """.trimIndent(),
        expectedMessage = "must not have an extension receiver or context parameters"
    )

    @Test
    fun providesPropertyOfNonService() = assertCompilationError(
        code = """
        @Service interface S
        class Unrelated
        @Provides(S::class) val provided: Unrelated = Unrelated()
        """.trimIndent(),
        expectedMessage = "not a subtype of service"
    )
```

- [ ] **Step 2: Run to verify failure**

Run: `./gradlew :sweetspi-tests:test --tests "dev.whyoleg.sweetspi.tests.CheckerTest.providesMutableProperty" --tests "dev.whyoleg.sweetspi.tests.CheckerTest.providesPropertyWithReceiver" --tests "dev.whyoleg.sweetspi.tests.CheckerTest.providesPropertyOfNonService"`
Expected: FAIL.

- [ ] **Step 3: Implement ProvidesPropertyChecker**

Replace `ProvidesPropertyChecker.kt` entirely:

```kotlin
/*
 * Copyright (c) 2026 Oleg Yukhnevich. Use of this source code is governed by the Apache 2.0 license.
 */

package dev.whyoleg.sweetspi.compiler.checkers

import dev.whyoleg.sweetspi.compiler.SweetNames
import org.jetbrains.kotlin.diagnostics.DiagnosticReporter
import org.jetbrains.kotlin.diagnostics.reportOn
import org.jetbrains.kotlin.fir.analysis.checkers.MppCheckerKind
import org.jetbrains.kotlin.fir.analysis.checkers.context.CheckerContext
import org.jetbrains.kotlin.fir.analysis.checkers.declaration.FirPropertyChecker
import org.jetbrains.kotlin.fir.declarations.FirProperty
import org.jetbrains.kotlin.fir.declarations.hasAnnotation

internal object ProvidesPropertyChecker : FirPropertyChecker(MppCheckerKind.Common) {
    context(context: CheckerContext, reporter: DiagnosticReporter)
    override fun check(declaration: FirProperty) {
        val session = context.session
        if (!declaration.hasAnnotation(SweetNames.Provides, session)) return

        if (!declaration.status.visibility.isAllowedForSweet()) {
            reporter.reportOn(declaration.source, SweetDiagnostics.PROVIDES_WRONG_VISIBILITY)
        }
        if (declaration.isVar) {
            reporter.reportOn(declaration.source, SweetDiagnostics.PROVIDES_PROPERTY_MUTABLE)
        }
        if (declaration.receiverParameter != null || declaration.contextParameters.isNotEmpty()) {
            reporter.reportOn(declaration.source, SweetDiagnostics.PROVIDES_PROPERTY_RECEIVER)
        }

        val providedType = declaration.symbol.resolvedReturnType
        val providedSymbol = providedType.serviceClassSymbol(session)
        declaration.providesServiceTypes(session).forEach { (annotation, serviceType) ->
            val serviceSymbol = serviceType.serviceClassSymbol(session) ?: return@forEach
            if (!serviceSymbol.isAnnotatedAsService(session)) {
                reporter.reportOn(annotation.source, SweetDiagnostics.PROVIDES_SERVICE_NOT_A_SERVICE, serviceSymbol.classId.asFqNameString())
            } else if (providedSymbol == null || !providedSymbol.isSweetSubclassOf(serviceSymbol.classId, session)) {
                reporter.reportOn(declaration.source, SweetDiagnostics.PROVIDES_TYPE_MISMATCH, serviceSymbol.classId.asFqNameString())
            }
        }
    }
}
```

Note: `declaration.isVar` is on `FirProperty`. `resolvedReturnType` is on the property symbol.

- [ ] **Step 4: Run the Task-5 tests to verify they pass**

Run the same command as Step 2.
Expected: PASS (all three).

- [ ] **Step 5: Commit**

```bash
git add sweetspi-compiler-plugin/src/main/kotlin/checkers/ProvidesPropertyChecker.kt \
        sweetspi-tests/src/test/kotlin/CheckerTest.kt
git commit -m "feat(checkers): validate @Provides on properties"
```

---

## Task 6: Positive controls + full suite run

**Files:**
- Modify: `sweetspi-tests/src/test/kotlin/CheckerTest.kt`

Guards against false positives: valid forms (already covered by `SimpleServiceTest`) must still compile through the new checkers.

- [ ] **Step 1: Add positive-control tests**

Append inside `CheckerTest`:

```kotlin
    @Test
    fun validObjectProviderCompiles() = assertCompiles(
        code = """
        @Service interface S
        @Provides(S::class) object Impl : S
        """.trimIndent()
    )

    @Test
    fun validGenericServiceCompiles() = assertCompiles(
        code = """
        @Service abstract class S<T>(val value: T)
        @Provides(S::class) object SString : S<String>("x")
        """.trimIndent()
    )

    @Test
    fun validFunctionWithAllDefaultParamsCompiles() = assertCompiles(
        code = """
        @Service interface S
        class Impl : S
        @Provides(S::class) fun provide(tag: String = "default"): S = Impl()
        """.trimIndent()
    )
```

- [ ] **Step 2: Run positive controls to verify they pass**

Run: `./gradlew :sweetspi-tests:test --tests "dev.whyoleg.sweetspi.tests.CheckerTest.validObjectProviderCompiles" --tests "dev.whyoleg.sweetspi.tests.CheckerTest.validGenericServiceCompiles" --tests "dev.whyoleg.sweetspi.tests.CheckerTest.validFunctionWithAllDefaultParamsCompiles"`
Expected: PASS. If a positive control fails, a checker has a false positive — fix the offending checker (likely the subtype-by-classifier logic for generics) before continuing.

- [ ] **Step 3: Run the entire CheckerTest class**

Run: `./gradlew :sweetspi-tests:test --tests "dev.whyoleg.sweetspi.tests.CheckerTest"`
Expected: all tests PASS.

- [ ] **Step 4: Run the existing positive suite to confirm no regression**

Run: `./gradlew :sweetspi-tests:test --tests "dev.whyoleg.sweetspi.tests.SimpleServiceTest"`
Expected: PASS (checkers must not reject any previously valid case).

- [ ] **Step 5: Commit**

```bash
git add sweetspi-tests/src/test/kotlin/CheckerTest.kt
git commit -m "test(checkers): add positive controls for valid @Service/@Provides usage"
```

---

## Self-review

**Spec coverage:** Every diagnostic in the spec's §2 table maps to a task — `@Service` (Task 2), shared `@Provides` + class (Task 3), function (Task 4), property (Task 5), positive controls (Task 6). `@JvmProvides` intentionally excluded per spec. All-errors and proper-diagnostics decisions honored (Task 1 uses `error0/error1` + renderer map). JVM single-target + one-test-per-violation honored.

**Placeholder scan:** No TBD/TODO; every code step shows full code. The three "verify via IDEA MCP" notes target genuinely version-sensitive symbol names (`modality`, `contextParameters`, `ClassId.isLocal`) and name the fallback — they are not placeholders for missing logic.

**Type consistency:** Helper names are stable across tasks — `isAllowedForSweet()`, `providesServiceTypes()`, `serviceClassSymbol()`, `isAnnotatedAsService()`, `isSweetSubclassOf()`, `SweetNames.ProvidesServiceArgument`, and all `SweetDiagnostics.*` keys match between `SweetDiagnostics.kt`, `SweetCheckerUtils.kt`, and the four checkers.

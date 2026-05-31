# SweetSPI Checkers Round 2 — IR member support, tidy, extra checkers

> **For agentic workers:** execute task-by-task with TDD; spec + quality review after each.

**Goal:** Support `@Provides` on object/companion-object member functions & properties in IR; reject the truly-unsupportable member cases (members of regular classes) and bare type-parameter provided types in FIR; flag duplicate `@Provides(X)` as an error; and make the IR generator's defensive code honest about FIR guarantees.

**Decisions (approved):** implement member support in IR (not just block); tidy IR comments + minor logic; add `PROVIDES_TYPE_PARAMETER` + duplicate checkers; duplicate severity = **error**.

## Verified APIs (Kotlin 2.3.20)
- `FirBasedSymbol<*>.getContainingClassSymbol(): FirClassLikeSymbol<*>?` (null ⇒ top-level). For checkers, import from `org.jetbrains.kotlin.fir.analysis.checkers` (verify; an equivalent exists in `org.jetbrains.kotlin.fir.resolve`). `FirClassSymbol.classKind` gives `ClassKind`; companion/nested objects are `ClassKind.OBJECT`.
- `ConeTypeParameterType` (`org.jetbrains.kotlin.fir.types`) — a provided type that is a bare type parameter.
- `IrParameterKind { DispatchReceiver, Context, ExtensionReceiver, Regular }` (`org.jetbrains.kotlin.ir.declarations`). `IrValueParameter.kind`.
- `irGetObject(classSymbol)` already used in `callServiceProvider`. `IrClass.isObject` already used.
- Verify any uncertain symbol via IDEA MCP (`search_symbol`/`read_file`, projectPath `/Users/Oleg.Yukhnevich/Projects/whyoleg/sweet-spi`); never unzip jars.

---

## Task R1: New diagnostics + duplicate helper

**Files:** `checkers/SweetDiagnostics.kt`, `checkers/SweetCheckerUtils.kt`. Plugin must compile (`./gradlew :sweetspi-compiler-plugin:compileKotlin`). No tests here (infra). Commit only these two files.

Add to `SweetDiagnostics` (DSL form, `KtElement`):
```kotlin
    val PROVIDES_FUNCTION_NOT_TOP_LEVEL_OR_OBJECT by error0<KtElement>()
    val PROVIDES_PROPERTY_NOT_TOP_LEVEL_OR_OBJECT by error0<KtElement>()
    val PROVIDES_TYPE_PARAMETER by error0<KtElement>()
    val PROVIDES_DUPLICATE_SERVICE by error1<KtElement, String>()
```
Add to the renderer map:
```kotlin
        map.put(SweetDiagnostics.PROVIDES_FUNCTION_NOT_TOP_LEVEL_OR_OBJECT, "@Provides function must be top-level or a member of an object")
        map.put(SweetDiagnostics.PROVIDES_PROPERTY_NOT_TOP_LEVEL_OR_OBJECT, "@Provides property must be top-level or a member of an object")
        map.put(SweetDiagnostics.PROVIDES_TYPE_PARAMETER, "@Provides provided type must be a concrete type, not a type parameter")
        map.put(SweetDiagnostics.PROVIDES_DUPLICATE_SERVICE, "service ''{0}'' is provided more than once by this declaration", CommonRenderers.STRING)
```

Add to `SweetCheckerUtils.kt` a shared duplicate reporter (context-parameter form, mirroring how checkers report):
```kotlin
context(context: CheckerContext, reporter: DiagnosticReporter)
internal fun FirAnnotationContainer.reportDuplicateProvidesServices(session: FirSession) {
    val seen = HashSet<ClassId>()
    providesServiceTypes(session).forEach { (annotation, serviceType) ->
        val id = serviceType.serviceClassSymbol(session)?.classId ?: return@forEach
        if (!seen.add(id)) {
            reporter.reportOn(annotation.source, SweetDiagnostics.PROVIDES_DUPLICATE_SERVICE, id.asFqNameString())
        }
    }
}
```
(Imports: `CheckerContext`, `DiagnosticReporter`, `reportOn`, `ClassId`. Verify `reportOn` works with the context parameter in a non-checker function — it does, as it's `context(...)`-based.)

---

## Task R2: IR object-member support + tidy (`SweetIrGenerationExtension.kt`)

Rewrite `callServiceProvider` so function/property providers work as members of an object/companion, and tidy the now-FIR-guaranteed asserts. Reference shape:

```kotlin
private fun IrBuilderWithScope.callServiceProvider(declaration: IrDeclaration): IrExpression = when (declaration) {
    is IrClass if declaration.isObject -> irGetObject(declaration.symbol)
    is IrClass                         -> {
        val visibleConstructors = declaration.constructors.filter {
            it.visibility == DescriptorVisibilities.PUBLIC || it.visibility == DescriptorVisibilities.INTERNAL
        }.toList()
        // no-arg first, else all-default; guaranteed to exist by PROVIDES_CLASS_NO_CONSTRUCTOR
        val constructor = visibleConstructors.firstOrNull { it.regularParameters.isEmpty() }
            ?: visibleConstructors.firstOrNull { it.regularParameters.all { p -> p.defaultValue != null } }
            ?: error("internal: suitable constructor guaranteed by FIR PROVIDES_CLASS_NO_CONSTRUCTOR")
        irCall(constructor)
    }
    is IrSimpleFunction                -> irCall(declaration).withProviderReceiver(declaration)
    is IrProperty                      -> {
        val getter = declaration.getter ?: error("internal: val getter guaranteed by FIR PROVIDES_PROPERTY_MUTABLE")
        irCall(getter).withProviderReceiver(declaration)
    }
    else                               -> error("Unexpected declaration type: ${declaration::class.simpleName}")
}
```

Helpers to add (names illustrative — confirm exact IR APIs via IDEA MCP):
- `IrFunction.regularParameters`: `parameters.filter { it.kind == IrParameterKind.Regular }` (excludes dispatch/extension/context). Use this for the all-default invariant on functions AND constructors so a member function's dispatch-receiver param is not mistaken for a required parameter.
- `IrCall.withProviderReceiver(declaration)`: if the callable's parent is an `IrClass` that `isObject`, set the dispatch-receiver argument to `irGetObject(parentObject.symbol)`; if parent is an `IrFile` (top-level) do nothing; otherwise `error("internal: top-level or object member guaranteed by FIR PROVIDES_*_NOT_TOP_LEVEL_OR_OBJECT")`. For the function case the callable is `declaration`; for the property case it's the getter, so resolve the owner from `declaration` (the property/function), not the getter. With the new IR API set the dispatch receiver via the call's dispatch-receiver argument slot (use the same `arguments[...]`/`dispatchReceiver` mechanism already used elsewhere in this file — confirm which is valid here via IDEA MCP).

Tidy: remove the stale `// should be handled in FIR` and `// TODO: check declaration type somewhere - maybe in FIR checker?` comments and the `// TODO: all of those should be checked in FIR too`; replace with brief notes that FIR now guarantees these. Keep the `error(...)` asserts as internal invariants with the clearer messages above. Leave `@JvmProvides` / JVM-flag TODOs untouched (out of scope).

**Verify:** `./gradlew :sweetspi-compiler-plugin:compileKotlin` succeeds. (Functional proof comes in R4.) Commit only `SweetIrGenerationExtension.kt`.

---

## Task R3: Function & property checkers — container + type-parameter + duplicate, with tests

**Files:** `checkers/ProvidesFunctionChecker.kt`, `checkers/ProvidesPropertyChecker.kt`, `checkers/ProvidesClassChecker.kt` (duplicate only), `sweetspi-tests/.../CheckerTest.kt`. TDD.

**Step 1 — failing tests (append to `CheckerTest`):**
```kotlin
    @Test
    fun providesFunctionInObjectCompiles() = assertCompiles(
        code = """
        @Service interface S
        class Impl : S
        object Holder { @Provides(S::class) fun provide(): S = Impl() }
        """.trimIndent()
    )

    @Test
    fun providesFunctionInClassRejected() = assertCompilationError(
        code = """
        @Service interface S
        class Impl : S
        class Holder { @Provides(S::class) fun provide(): S = Impl() }
        """.trimIndent(),
        expectedMessage = "@Provides function must be top-level or a member of an object"
    )

    @Test
    fun providesPropertyInClassRejected() = assertCompilationError(
        code = """
        @Service interface S
        class Impl : S
        class Holder { @Provides(S::class) val provided: S = Impl() }
        """.trimIndent(),
        expectedMessage = "@Provides property must be top-level or a member of an object"
    )

    @Test
    fun providesFunctionTypeParameter() = assertCompilationError(
        code = """
        @Service interface S
        @Provides(S::class) fun <T : S> provide(): T = error("x")
        """.trimIndent(),
        expectedMessage = "must be a concrete type, not a type parameter"
    )

    @Test
    fun providesDuplicateService() = assertCompilationError(
        code = """
        @Service interface S
        @Provides(S::class) @Provides(S::class) object Impl : S
        """.trimIndent(),
        expectedMessage = "is provided more than once"
    )
```

**Step 2 — run, confirm failures (UnexpectedBuildSuccess / message-absent).**

**Step 3 — implement:**
- In `ProvidesFunctionChecker` and `ProvidesPropertyChecker`, after the existing receiver/suspend/val checks, add:
  - container check: `val container = declaration.symbol.getContainingClassSymbol(); val ok = container == null || (container is FirClassSymbol && container.classKind == ClassKind.OBJECT)`; if `!ok` report `PROVIDES_FUNCTION_NOT_TOP_LEVEL_OR_OBJECT` (resp. `_PROPERTY_`).
  - type-parameter: compute `returnType = declaration.symbol.resolvedReturnType`; `isTypeParam = returnType is ConeTypeParameterType`.
  - call `declaration.reportDuplicateProvidesServices(session)` once.
  - service loop using `when`: service-not-a-service → `PROVIDES_SERVICE_NOT_A_SERVICE`; else `isTypeParam` → `PROVIDES_TYPE_PARAMETER` (report on `declaration.source`); else `providedSymbol == null || !isSweetSubclassOf` → `PROVIDES_TYPE_MISMATCH`. (`providedSymbol = returnType.serviceClassSymbol(session)`.)
- In `ProvidesClassChecker`, add a single `declaration.reportDuplicateProvidesServices(session)` call alongside the existing per-service loop (classes can also repeat `@Provides`).

Mirror existing style; prefer symbol-level APIs (`-Werror`). Verify `getContainingClassSymbol`/`classKind`/`ConeTypeParameterType` imports via IDEA MCP if needed.

**Step 4 — run the 5 new tests + the full `CheckerTest`; all pass. Step 5 — commit the 4 files.**

---

## Task R4: Functional object-member load test + full regression

**Files:** `sweetspi-tests/.../SimpleServiceTest.kt` (add cases mirroring existing `testFunction_simple`/`testProperty`).

Add multiplatform functional tests proving member providers actually load:
```kotlin
    @Test
    fun testFunction_inObject() = testSingleSimpleService(
        mainCode = $$"""
        class SimpleServiceImpl(val string: String) : SimpleService {
            override fun toString() = "SimpleServiceImpl($string)"
        }
        object Provider { @Provides(SimpleService::class) fun provide() = SimpleServiceImpl("default") }
        """.trimIndent(),
        testCode = """
        assertEquals("SimpleServiceImpl(default)", provider.value.toString())
        """.trimIndent()
    )

    @Test
    fun testProperty_inCompanion() = testSingleSimpleService(
        mainCode = $$"""
        class SimpleServiceImpl(val string: String) : SimpleService {
            override fun toString() = "SimpleServiceImpl($string)"
        }
        class Host { companion object { @Provides(SimpleService::class) val provided = SimpleServiceImpl("default") } }
        """.trimIndent(),
        testCode = """
        assertEquals("SimpleServiceImpl(default)", provider.value.toString())
        """.trimIndent()
    )
```
(If a companion-object member needs the enclosing class to be referenced, adjust the snippet so the provider is reachable; confirm the load test passes on JVM at minimum.)

**Verify:** run `:sweetspi-tests:test --tests CheckerTest --tests SimpleServiceTest --rerun-tasks` → BUILD SUCCESSFUL (CheckerTest fast; SimpleServiceTest multiplatform). If a target is flaky for reasons unrelated to providers, report rather than mask. Commit `SimpleServiceTest.kt`.

## Self-review
- Coverage: object-member support (IR R2 + functional R4), regular-class member rejected (R3), type-parameter rejected (R3), duplicate error (R1+R3), IR tidy (R2).
- No placeholders; symbol/IR API names to verify flagged with the IDEA-MCP fallback.
- Consistency: all three `@Provides` checkers call `reportDuplicateProvidesServices`; function/property share the container + type-param pattern.

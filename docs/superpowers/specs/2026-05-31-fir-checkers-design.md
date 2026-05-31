# SweetSPI FIR Checkers + Checker Tests — Design

Date: 2026-05-31

## Goal

Implement K2 FIR checkers that validate correct usage of `@Service` and `@Provides`
annotations, reporting compilation **errors** before IR generation runs. Add tests that
compile bad code and assert the expected diagnostic messages appear in the build output.

## Decisions (agreed)

- Implement the full checker suite for `@Service` and `@Provides` (class / function / property).
- **Skip** `@JvmProvides` — it has no IR generation support yet.
- Every violation is a compilation **error** (no warnings).
- Use **proper FIR diagnostics**: a `KtDiagnosticsContainer` with diagnostic factories + a
  renderer map, rather than inline reporter strings.
- Checker tests compile against a **JVM single-target** project (frontend errors are
  platform-agnostic, so this is sufficient and fast).
- **One test per violation**, plus a couple of positive controls.
- When unsure whether a compiler API exists in Kotlin 2.3.20, verify via the IDEA MCP server
  (or ask) — do not unzip jars.

## Context

- Kotlin **2.3.20**; plugin depends on `compiler-embeddable`.
- `SweetFirCheckers` is an empty `FirAdditionalCheckersExtension`, already registered in
  `SweetFirExtensionRegistrar` (`SweetCommandLineProcessor.kt`).
- `SweetIrGenerationExtension` contains `error(...)`/`require(...)` sites with
  `// should be handled in FIR` comments — these enumerate what FIR must reject:
  - no suitable constructor for a `@Provides` class
  - function parameters must all be default
  - property must have a parameterless getter
- Annotation contracts (`sweetspi-runtime`):
  - `@Service` → interfaces or abstract classes only.
  - `@Provides(service: KClass)` → repeatable; CLASS (object / no-arg or all-default ctor),
    PROPERTY (immutable, non-suspend, getter/initializer), FUNCTION (non-suspend, no args or
    all-default, no receiver).
- Tests run real Gradle builds (`GradleRunner`) against published dev artifacts; `gradle(...)`
  exposes `BuildResult.output` and `expectFailure = true` → `buildAndFail()`.

## 1. File structure (`sweetspi-compiler-plugin/src/main/kotlin/`)

```
SweetFirCheckers.kt                  # FirAdditionalCheckersExtension — wires checkers into declarationCheckers
checkers/SweetDiagnostics.kt         # KtDiagnosticsContainer (factories) + renderer map + registration
checkers/SweetCheckerUtils.kt        # shared helpers (see §4)
checkers/ServiceChecker.kt           # FirRegularClassChecker for @Service
checkers/ProvidesClassChecker.kt     # FirRegularClassChecker for @Provides on class
checkers/ProvidesFunctionChecker.kt  # FirSimpleFunctionChecker for @Provides on function
checkers/ProvidesPropertyChecker.kt  # FirPropertyChecker for @Provides on property
```

`SweetFirCheckers` overrides `declarationCheckers`:
- `regularClassCheckers = setOf(ServiceChecker, ProvidesClassChecker)`
- `simpleFunctionCheckers = setOf(ProvidesFunctionChecker)`
- `propertyCheckers = setOf(ProvidesPropertyChecker)`

## 2. Diagnostics (all `error`, declared in `SweetDiagnostics`)

| Key | Message | Reported on |
|---|---|---|
| `SERVICE_WRONG_TARGET` | `@Service is only applicable to interfaces or abstract classes` | class |
| `SERVICE_WRONG_VISIBILITY` | `@Service declaration must be public or internal` | class |
| `PROVIDES_AND_SERVICE_TOGETHER` | `declaration cannot be annotated with both @Service and @Provides` | declaration |
| `PROVIDES_SERVICE_NOT_A_SERVICE` | `'{X}' in @Provides is not annotated with @Service` | annotation arg |
| `PROVIDES_TYPE_MISMATCH` | `provided type is not a subtype of service '{X}'` | declaration / return type |
| `PROVIDES_WRONG_VISIBILITY` | `@Provides declaration must be public or internal` | declaration |
| `PROVIDES_CLASS_NOT_INSTANTIABLE` | `@Provides class must be an object or an instantiable class (not abstract/sealed/enum/annotation/interface)` | class |
| `PROVIDES_CLASS_INNER` | `@Provides class must not be inner` | class |
| `PROVIDES_CLASS_LOCAL` | `@Provides class must not be local` | class |
| `PROVIDES_CLASS_NO_CONSTRUCTOR` | `@Provides class must have a public or internal constructor with no parameters or all-default parameters` | class |
| `PROVIDES_FUNCTION_RECEIVER` | `@Provides function must not have an extension receiver or context parameters` | function |
| `PROVIDES_FUNCTION_SUSPEND` | `@Provides function must not be suspend` | function |
| `PROVIDES_FUNCTION_PARAMETERS` | `@Provides function parameters must all have default values` | function |
| `PROVIDES_PROPERTY_MUTABLE` | `@Provides property must be a val` | property |
| `PROVIDES_PROPERTY_RECEIVER` | `@Provides property must not have an extension receiver or context parameters` | property |

`{X}` is rendered from the resolved service symbol. Exact wording may be tuned during
implementation; tests assert on a stable substring of each message.

## 3. Checker logic

- **ServiceChecker** (only when `@Service` present): kind is interface or abstract class;
  visibility public/internal.
- **ProvidesClassChecker** (only when `@Provides` present): cross-check no `@Service`; kind
  instantiable; not inner; not local; has a suitable constructor (object ⇒ ok); then shared
  checks (§4) for each `@Provides(X)`.
- **ProvidesFunctionChecker**: no receiver / context params; not suspend; all value params have
  defaults; shared checks against the return type.
- **ProvidesPropertyChecker**: is `val`; no receiver / context params; shared checks against the
  property type.

## 4. Shared helper (`SweetCheckerUtils`)

- `providesServiceTypes(declaration)` → for each `@Provides(X::class)` annotation, resolve `X` to
  its `FirClassSymbol`.
- `isProvidedTypeValid(providedType, serviceSymbol)` → subtype comparison **by classifier**,
  walking supertypes, so generic services validate correctly
  (`@Provides(SimpleService::class) object … : SimpleService<String>` is valid — matches the
  existing `testGenericService`).
- visibility helper: public or internal.

## 5. Test plan — `sweetspi-tests/src/test/kotlin/CheckerTest.kt`

`class CheckerTest : TestProjectBasedTest()`, JVM single-target. Helper:

```kotlin
private fun assertCompilationError(
    @Language("kotlin") code: String,
    expectedMessage: String,
) = context(project) {
    bootstrapKotlinJvmProject()
    file("src/main/kotlin/Subject.kt").kotlin(
        """
        import dev.whyoleg.sweetspi.*

        $code
        """.trimIndent()
    )
    gradle("compileKotlin", expectFailure = true) {
        assertTrue(output.contains(expectedMessage), "expected '$expectedMessage' in build output")
    }
}
```

- One `@Test` per violation (~15), each a minimal bad snippet asserting its message.
- A few positive controls asserting valid code still compiles (generic service; function with
  all-default params), to guard against false positives.

## 6. Implementation notes (verify against Kotlin 2.3.20)

- Exact checker `check(...)` signature (context-parameter vs positional `context`/`reporter`) and
  the diagnostics registration mechanism (`RootDiagnosticRendererFactory.registerFactory` vs a
  `getRendererFactory()` override on the container) are version-sensitive. Confirm via IDEA MCP /
  `compiler-embeddable` 2.3.20 during implementation. The set of checks, messages, and test shape
  are unaffected.
- The corresponding IR `error()`/`require()` sites become unreachable safety nets once FIR
  rejects upstream; they can stay as defensive guards.
```

## Out of scope

- `@JvmProvides` checkers and IR generation.
- Warning-level diagnostics.
- Multiplatform-specific checker behavior (errors are frontend, identical across targets).

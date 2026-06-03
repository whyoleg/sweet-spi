# sweet-spi Roadmap — Foundation & JVM Compatibility

Date: 2026-06-02

This is a **sequenced roadmap**, not a single-feature spec. It captures the current state, the
gap analysis, and a milestone plan (M0–M6) with dependencies and exit criteria. Each milestone is
later handed to `writing-plans` to produce its own task-by-task implementation plan, starting with
M0. The document has two registers: a **human overview** (this top half) and **agent detail** (per
milestone, lower half).

---

## Part 1 — Human overview

### Where we are

sweet-spi is a Kotlin **compiler-plugin** based SPI library (migrated from the older KSP processor —
the README still describes the old KSP flow and is out of scope to fix here). Three modules:

- **`sweetspi-runtime`** — frozen public API: `@Service`, `@Provides`, `@JvmProvides`,
  `JavaServiceLoaderCompatibilityMode`, `ServiceLoader`, `ServiceProvider`, plus internal
  `ServiceProviderRegistry` / `ServiceProviderRegistryHolder`. JVM delegates to
  `java.util.ServiceLoader`; non-JVM uses `@EagerInitialization` + an associated-object registry.
- **`sweetspi-compiler-plugin`** — FIR checkers (`@Service`/`@Provides` validation, proper
  `KtDiagnosticsContainer` diagnostics) + IR generation (a `$ServiceProvider` nested class per
  `@Service`; a `$ServiceProviderRegistry` per service on klib; a provider-impl class per
  `@Provides`; JVM `META-INF/services` resources + a `module-info.properties` file).
- **`sweetspi-gradle-plugin`** — wires the compiler plugin, adds the runtime dependency, and
  contains a `ModuleInfoJavaBuilder` (JPMS) that is **written but not wired in**.

What already works: `@Service` on interface/abstract class; `@Provides` on object / instantiable
class / top-level or object-member function / property; the full checker suite; JVM
`META-INF/services` generation; klib eager-init registration; multiplatform functional tests across
jvm / macosArm64 / js / wasmJs / wasmWasi; JVM-only checker tests.

### Gap analysis

| # | Gap | Evidence |
|---|---|---|
| G1 | FIR/IR code is a 569-line monolith mixing collectors, generators, and JVM/klib branching | `ir/SweetIrGenerationExtension.kt` |
| G2 | `ServiceLoaderUsagesCollector` is fully stubbed (commented body, returns empty) → `uses` always empty | `ir/SweetIrGenerationExtension.kt:156` |
| G3 | `Service` compat enum is half-wired: runtime loader honors it, IR always emits NO_COMPATIBILITY entries | `// TODO: JVM enum flags` in IR; `Service.kt` doc |
| G4 | `@JvmProvides` declared in runtime but no checker, no IR | `ServiceLoader.kt:79` |
| G5 | JVM `ServiceLoader.load` does a reflective `declaredClasses` lookup → defeats R8 | `ServiceLoader.jvm.kt:64` |
| G6 | `module-info.properties` is produced but never consumed; gradle wiring commented out; `exports` lists every package | `SweetSpiPlugin.kt:77`, `ModuleInfoJavaBuilder.kt` |
| G7 | `@JsExport` SPI exists only as commented sketches | `wip.kt` |
| G8 | **Cross-module SPI broken on JVM**: `$ServiceProvider` is generated in IR, so it is not in the consumable ABI; a dependent module's `@Provides` fails to resolve it (`Provider class not found for service type`) and `:provider` compilation fails | `ir/SweetIrGenerationExtension.kt:293` — confirmed 2026-06-03 by `MultiModuleServiceTest.testJvm` |

### North star

Per decisions: **(1) a clean foundation** and **(2) JVM-ecosystem compatibility** are the near-term
priorities. Path-to-1.0 polish and feature breadth (JsExport) come later. Runtime public API is
**frozen** (implementations conform to it); the **gradle-plugin API is free to change**. Docs/README
are out of scope.

### Dependency map

```
                    ┌─ multi-module exec tests ─┐
   M0 SAFETY NET ───┤                           │   (protect the refactor)
                    └─ KMP checker tests ────────┘
                              │
   M1 FOUNDATION ─────────────┼──────────────────
     • FIR/IR refactor (split monolith, delete dead/wip code)
     • ServiceLoader-usage collection   ◀── shared prerequisite for M4 + M5
                              │
        ┌─────────────────────┼───────────────────────┐
   JVM COMPAT                  │                        │
     • M2 Service compat enum in IR (META-INF per mode)
              │
              ├── M3 @JvmProvides (checker + META-INF)
              ├── M4 R8 load() rewrite   (needs usage-collection + M2 mode)
              └── M5 module-info.java    (needs usage `uses` + M2 `provides`)
                              │
   M6 SPIKE ──────────────────┴───────────────────────
     • @JsExport SPI  (design-only research spike)
```

### Milestone table

| ID | Goal | Depends on | Kind | Status |
|----|------|-----------|------|--------|
| M0 | Multi-module + KMP-checker regression net | — | tests | done |
| M1 | Cross-module fix (G8) + FIR/IR refactor | M0 | refactor + fix | done |
| M1b | Test consolidation & MPP hierarchy alignment | M1 | tests | done |
| M1c | KMP scenario test DSL (unify checker/topology/forms) | M1b | tests | in progress |
| M2 | Honor `Service` compat enum in IR/resources + `ServiceLoader` usage collection + decide JVM-test DSL | M1 | feature | planned |
| M3 | `@JvmProvides` checker + IR | M1 | feature | planned |
| M4 | R8-friendly `ServiceLoader.load` IR rewrite (JVM) | M1, M2 | feature | planned |
| M5 | Generate `module-info.java` in gradle plugin | M1, M2 | feature | planned |
| M6 | `@JsExport` SPI design spike | (independent) | research | planned |

Ordering rationale (re-sequenced from the originally-listed order): tests come **first** so the big
M1 refactor has a regression net; usage-collection is folded **into M1** because it is the single
shared prerequisite for both M4 and M5; M2 precedes M4/M5 because both must know which class to
reference per compatibility mode; M6 is design-only and last.

---

## Part 2 — Agent detail

Conventions for every milestone: verify uncertain compiler APIs via the **IDEA MCP server**
(`search_symbol` / `read_file`, `projectPath=/Users/Oleg.Yukhnevich/Projects/whyoleg/sweet-spi`) —
**never unzip jars**. Custom FIR diagnostics use the `by error*<KtElement>()` DSL in a
`KtDiagnosticsContainer` (see `fir/SweetDiagnostics.kt`). Prefer symbol-level FIR APIs (`-Werror`).
Kotlin version is **2.3.20**; plugin depends on `compiler-embeddable`. Functional tests run real
Gradle builds via `TestProjectBasedTest` against published dev artifacts.

### M0 — Regression safety net

**Why:** lock current cross-module + checker behavior before refactoring in M1.

**Finding (2026-06-03):** writing the multi-module test surfaced **G8** — cross-module SPI does **not**
currently work on JVM (the IR-generated `$ServiceProvider` isn't resolvable from a dependent module).
Per decision, the multi-module tests are committed but `@Ignore`d (with a pointer to M1); they *encode*
the cross-module requirement and become M1's acceptance criteria. M0 therefore pins the behavior that
*does* work today (single-module discovery + KMP checkers) and documents the gap that does not.

**Scope**
- Multi-module functional test: ≥3 Gradle modules in one test project — `@Service` in module A,
  `@Provides` impl in module B, `ServiceLoader.load` consumer in module C — asserting discovery
  works. Cover **JVM and ≥1 klib target** (e.g. macosArm64). Include a variant where the provider
  lives in a module the consumer only depends on transitively (the "library" promise).
- KMP checker tests: make the checker suite run against a multiplatform project. Either parametrize
  `CheckerTest` over a JVM project and a KMP project, or add a `CheckerKmpTest` reusing the same
  snippets. Confirms diagnostics are identical (they are frontend / `MppCheckerKind.Common`).

**Files**
- `sweetspi-tests/src/test/kotlin/api/TestProject.kt` + `TestProjectBasedTest.kt` — add multi-module
  project bootstrapping (currently single-module only; see `bootstrapKotlinJvmProject` /
  `bootstrapKotlinMultiplatformProject`).
- New `sweetspi-tests/src/test/kotlin/MultiModuleServiceTest.kt`.
- `sweetspi-tests/src/test/kotlin/CheckerTest.kt` — KMP variant/parametrization.

**Exit criteria:** the KMP checker tests and the single-module suite are green; the multi-module tests
are committed `@Ignore`d (documenting G8) and must remain stable through M1's refactor, then be
un-ignored and turned green by M1.

### M1 — Foundation: FIR/IR refactor + usage collection

**Why:** G1, G2, **G8**. Clean structure to build M2–M5 on, the shared usage-collection prerequisite,
and the fix for cross-module discovery.

**Scope**
- **Fix cross-module discovery (G8): generate `$ServiceProvider` (and the klib `$ServiceProviderRegistry`
  / holder) in FIR**, not IR, so they become part of the consumable ABI and a dependent module's
  `@Provides` can resolve `<Service>$ServiceProvider` (and `@EagerInitialization` registration can
  reference the registry) across module boundaries. This is the core behavioral change of M1; the IR
  generators then only emit bodies/impl classes for declarations the frontend already knows about.
  Acceptance: the `@Ignore` comes off `MultiModuleServiceTest` and it goes green on JVM + klib.
- **Refactor** `ir/SweetIrGenerationExtension.kt` (currently 569 lines) into focused files. Suggested
  layout (confirm naming as you go): orchestration entry point; `ServiceProviderGenerator`
  (`@Service` → `$ServiceProvider` nested + klib `$ServiceProviderRegistry`); 
  `ServiceProviderImplementationGenerator` (`@Provides` → impl class + klib initializer + JVM
  resource bookkeeping); collectors (packages, usages); shared IR builders
  (`annotateWith`, provider-impl construction). Keep the JVM vs klib split explicit via the existing
  `JvmSweetIrPluginContext` / `KlibSweetIrPluginContext` sealed contexts.
- **Delete** `sweetspi-runtime/src/commonMain/kotlin/wip.kt` (design graveyard; its live ideas are
  captured in M2/M4/M6).
- **`ServiceLoaderUsagesCollector` moved to M2** (2026-06-03): it only feeds the JVM `module-info`
  `uses` section, so it belongs with the JVM-compatibility work, not the cross-module fix. The stub
  stays as-is until M2. Its `callableId`/reified-`inline` extraction notes now live in the M2 scope.

**Files:** `sweetspi-compiler-plugin/src/main/kotlin/{fir,ir}/**` (new FIR declaration-generation
extension for `$ServiceProvider`/registry/holder), delete `wip.kt`.

**Exit criteria:** the M0 single-module + KMP-checker tests stay green; **the `@Ignore` is removed from
`MultiModuleServiceTest` and it passes on JVM + klib** (cross-module fixed — DONE); the IR monolith is
split into focused files and `wip.kt` is deleted; `./gradlew :sweetspi-compiler-plugin:compileKotlin`
succeeds with `-Werror`. Note: M1 is **not** purely structural — moving `$ServiceProvider`/registry
generation IR→FIR was a deliberate behavioral fix (G8, done); the refactor rides along. (Usage
collection moved to M2.)

### M1b — Test consolidation & MPP hierarchy alignment

**Why:** the test suite grew organically (jvm/kmp, single/multi-module, provider forms, source-set
hierarchies) with inconsistent target matrices and ad-hoc `dependsOn` setup. Align it so coverage is
self-evident before building more features on top.

**Scope**
- **Migrate** the custom source-set wiring from manual `dependsOn` to
  `applyDefaultHierarchyTemplate { common { group("nonJvm"){…}; group("jvmAndNative"){…} } }`
  (auto-creates the `*Test` source sets too).
- **One canonical KMP target matrix** for execution tests: `jvm`, `macosArm64` (run), `linuxX64`
  (compile-only on macOS host), `wasmJs` (run), `wasmWasi` (run). `js` only in the known-limitation
  tracker (cross-module js is broken — see M6). Principle: don't duplicate `js` where `wasmJs` covers it.
- **Standard execution-project helper** that declares the canonical matrix + the shared hierarchy
  (`nonJvm`/`jvmAndNative` always created), so tests just place files in `src/<sourceSet>/kotlin`.
  Checker tests keep their minimal jvm-only setup.
- **Reorganize execution tests by topology** for clear coverage: `ProviderFormsTest`
  (object/class/ctor variants/function/property/companion/generic), `SourceSetPlacementTest`
  (commonMain / `nonJvm` / `jvmAndNative`), `CrossModuleTest` (multi-module jvm + kmp, provider in an
  intermediate, + the `@Ignore`d js cross-module tracker). Keep `CheckerTest` (jvm) + `CheckerKmpTest`.

**Exit criteria:** all execution tests run on the canonical matrix via the shared helper; no manual
`dependsOn`; the topology split is in place; full `:sweetspi-tests:test` green (js tracker `@Ignore`d).

### M1c — KMP scenario test DSL

**Why:** the three KMP test kinds (checkers, service/provider placement across modules+source-sets,
provider declaration forms) are the same shape — *put code somewhere, build the project, run across the
target axis, assert per target*. Encode that once so coverage is a matrix, not hand-rolled per test.

**Decisions (2026-06-03):** build a **KMP-only** DSL now; a separate JVM DSL (and whether to share
scenario definitions across jvm/kmp) is deferred to **M2** (JVM specifics). Drop the `js` cross-module
tracker entirely — js returns later by adding the `js` target to the canonical matrix once fixed. Keep
code **inline** in tests (no extracted fixtures); delete `Fixtures.kt`.

**Scope**
- `api/KmpScenario.kt`: a project-spec DSL (modules + dependencies + per-source-set files via logical
  layers `commonMain`/`commonTest`/`nonJvmMain`/`nonJvmTest`/`jvmAndNativeMain`/`jvmAndNativeTest`) on
  the canonical matrix, plus two runners:
  - `kmpCheckerScenario(code) { failsWith(msg) | compiles() }` — runs `compileKotlin<Target>` for **every**
    canonical target (jvm, macosArm64, linuxX64, wasmJs, wasmWasi), asserting the diagnostic / success
    **per target call** (`--continue` so all targets are checked in one build).
  - `kmpExecutionScenario { module(...){ ... } }` — builds the (multi-)module project, then runs the
    consumer's test tasks for the targets that include the test's source set (compile-only for linuxX64),
    asserting runtime discovery per target.
- Rewrite the KMP tests onto it: `CheckerKmpTest` (all checker cases, per target), `TopologyTest`
  (merges source-set placement + cross-module into single scenarios mixing modules **and** source sets),
  `ProviderFormsTest` KMP cases. Existing plain-JVM tests (`CheckerTest`, jvm execution sm…) stay as-is
  until M2.

**Exit criteria:** KMP checker + execution + forms run through the DSL; `TopologyTest` exercises
module-and-source-set relations in single cases; `Fixtures.kt` and the js tracker are gone; full
`:sweetspi-tests:test` green.

**Risk note:** if the reified-inline type argument cannot be recovered at IR call sites, record the
limitation here and adjust M4's approach accordingly before starting M4.

### M2 — `Service` compat enum honored in IR

**Why:** G3. `javaServiceLoaderCompatibilityMode` is read by the JVM runtime loader
(`ServiceLoader.jvm.kt:34`) but IR always emits NO_COMPATIBILITY-style entries.

**Scope** — read the enum argument of `@Service(javaServiceLoaderCompatibilityMode = …)` and emit
JVM `META-INF/services` accordingly:
- `NO_COMPATIBILITY` → `META-INF/services/<pkg>.X$ServiceProvider` listing impl provider classes
  (current behavior).
- `PROVIDE_COMPATIBILITY` → entries for **both** `X` and `X$ServiceProvider`.
- `FULL_COMPATIBILITY` → `META-INF/services/<pkg>.X` listing the impl class `X_Impl` **directly**, so
  plain `java.util.ServiceLoader.load(X.class)` works. Document the constraint that object/function
  providers may be unsupported in this mode without extra glue (matches the runtime KDoc).

Resolve the enum value in FIR or IR (verify how to read an enum annotation argument via IDEA MCP).
The resource-writing site is `generateJvmServices` in the (refactored) IR; the `serviceId`/impl-id
mapping logic (`toJvmString`) already exists.

- **Implement `ServiceLoaderUsagesCollector`** (moved from M1, 2026-06-03): walk IR bodies, match calls
  whose `callableId` is in `SweetNames.AllServiceLoaderCalls` (sweetspi `ServiceLoader.load` reified +
  `KClass` overloads, and `java.util.ServiceLoader.load`), extract the **service type** from the reified
  type argument / `KClass` value argument, return `Set<ClassId>` feeding the JVM `module-info`
  `uses` section (consumed in M5). The reified overload is `inline` — confirm how the type argument
  survives to IR at the call site (verify via IDEA MCP); if fully inlined, target the resulting
  `java.util.ServiceLoader.load(...)` shape or the non-inline `load(KClass)` overload. This finding also
  informs M4. Add a focused test asserting the right `ClassId`s per `load` form.

**Files:** refactored IR generators + resource writer; `ir` usage collector.

**Exit criteria:** one functional test per mode (JVM); for `FULL_COMPATIBILITY`, a test where a
**Java** consumer (or plain `java.util.ServiceLoader`) loads the service. Existing
NO_COMPATIBILITY tests unchanged.

### M3 — `@JvmProvides`

**Why:** G4. Runtime declares `@JvmProvides` as the no-`@Service` escape hatch ("no checks, just
META-INF generation; error if interface has `@Service`").

**Scope**
- **Checker** (new `fir/JvmProvidesChecker.kt` + diagnostics in `SweetDiagnostics.kt`): applies to
  classes; **error if the referenced service type carries `@Service`** (use `@Service` →
  `@Provides`, not `@JvmProvides`); provided type must be a subtype of the referenced service;
  visibility public/internal. Keep checks minimal per the runtime contract.
- **IR / resources** (JVM only): emit `META-INF/services/<service-fqn>` listing the impl class
  directly — no `$ServiceProvider` nested class, no klib registry. On non-JVM platforms `@JvmProvides`
  is a no-op (or a checker error — decide and document; no-op is simplest given JVM-only intent).

**Files:** `fir/JvmProvidesChecker.kt`, `fir/SweetDiagnostics.kt`, `fir/SweetFirExtensionRegistrar.kt`
(register the checker), refactored IR JVM path.

**Exit criteria:** checker tests (`@Service`-on-target rejected; type mismatch rejected; valid case
compiles); functional test where Java **and** Kotlin consumers load the provider via plain
`java.util.ServiceLoader`.

### M4 — R8-friendly `ServiceLoader.load` IR rewrite (JVM)

**Why:** G5. `getProviderClass` does `cls.declaredClasses.firstOrNull { simpleName == "$ServiceProvider" }`
(`ServiceLoader.jvm.kt:64`) — reflective, so R8 can't fold the lookup.

**Scope** — JVM-only IR lowering that rewrites `sweetspi.ServiceLoader.load<T>()` / `.load(T::class)`
call sites into a direct expression built on `java.util.ServiceLoader.load(<classLiteral>, …)`,
removing the reflective indirection, so R8's built-in ServiceLoader optimization applies. The class
literal chosen must match the compat mode (M2): `T$ServiceProvider::class.java` for
NO_COMPATIBILITY, `T::class.java` for FULL_COMPATIBILITY. Reuse M1's call-site detection. Klib /
native link-time optimization is **explicitly out of scope**.

Open implementation question to resolve at plan time (carry M1's finding): whether to transform the
reified-inline call body, the non-inline `load(KClass)`, or the resulting JSL call — and whether the
JVM runtime `ServiceLoader` can be simplified once call sites are rewritten.

**Files:** new IR lowering in the JVM context; possibly simplify `ServiceLoader.jvm.kt` internals
(runtime API stays frozen — only private impl may change).

**Exit criteria:** functional test proving load behavior is unchanged post-rewrite; a documented R8
(or AGP `minifyEnabled`) verification showing the reflective lookup is gone / foldable. If a full R8
shrink test is infeasible in `sweetspi-tests`, document the manual verification steps instead.

### M5 — `module-info.java` generation (gradle plugin)

**Why:** G6. Compiler writes `build/generated/sweetspi/<sourceSet>/module-info.properties`
(`# exports / # uses / # provides`), `ModuleInfoJavaBuilder` + `fillFromProperties` can render it,
but `SweetSpiPlugin` never consumes it and the output wiring is commented out (`SweetSpiPlugin.kt:77`).

**Scope**
- Wire the compiler-produced `module-info.properties` → `ModuleInfoJavaBuilder` →
  generated `module-info.java`, placed on the JVM compilation; fix the commented-out
  `outputs.files(moduleInfoPropertiesPath)` wiring (the "why does it create a directory" TODO).
- `uses` is now populated from M1; `provides` naming must be consistent with the M2 compat mode.
- Refine `exports` — currently `PackagesCollector` exports **every** package. Decide a sensible
  default (e.g. all packages, or only those with public declarations) and expose **gradle DSL**
  (module name, exports/opens/requires overrides) — gradle-plugin API is free to change.
- Decide opt-in vs automatic (JPMS affects all consumers); default likely opt-in via DSL.

**Files:** `sweetspi-gradle-plugin/src/main/kotlin/jpms/**`, `SweetSpiPlugin.kt`,
`SweetSpiProperties.kt`; gradle-plugin unit test (`ModuleInfoJavaBuilderTest.kt` exists);
new JPMS functional test in `sweetspi-tests`.

**Exit criteria:** a JPMS execution test — a modular JVM app (`module-info.java` present) that
compiles with the generated descriptor and loads services at runtime via `ServiceLoader`.

### M6 — `@JsExport` SPI design spike (research, design-only)

**Why:** G7. Only commented sketches exist (in the now-deleted `wip.kt` — preserve the three options
below before deletion in M1).

**Concrete motivation found in M1 (2026-06-03):** with G8 fixed, cross-module SPI now works on JVM
(META-INF), native (whole-program link), **and wasmJs**, but **not on js**: a `@Provides` in a
separately-linked module registers via an `@EagerInitialization` property, and on js that module's
top-level initializers run only when the module is actually loaded — the consumer reaches the provider
module only reflectively (associated-object registry), so it is never loaded and `load()` returns 0.
(wasmJs does not exhibit this.) Encoded as the `@Ignore`d
`MultiplatformHierarchyTest.multiModuleProviderInNonJvm_js` (un-ignore when fixed). Single-module js SPI
is unaffected and works.

**Scope** — evaluate and recommend among the three approaches:
1. `@JsExport`-based export working for **both** js and wasm-js (a `@JsExport` service surface).
2. Wasm **multi-module** compilation (per-module exported service factories).
3. Wasm **WIT** (component-model) export, i.e. an `@WitExport`-like surface.

Produce a follow-up design doc (`docs/superpowers/specs/`) with a recommendation and a rough cost.
**No implementation** in this roadmap.

**Exit criteria:** a committed spike doc with a recommended approach.

---

## Out of scope (this roadmap)

- README / website / docs updates (stale KSP references).
- klib / native link-time `ServiceLoader` optimization (KT-75442).
- Any `@JsExport` implementation (M6 is design-only).
- New runtime public-API surface (`loadOne`, `@ServiceInfo`) — additive, future.

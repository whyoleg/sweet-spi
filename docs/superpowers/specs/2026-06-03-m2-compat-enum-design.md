# M2 — `Service` compat enum honored in IR + JVM-test DSL

Date: 2026-06-03

Milestone M2 of the [roadmap](2026-06-02-roadmap-design.md). This spec is the design handed to
`writing-plans` to produce the task-by-task implementation plan.

---

## Summary

M2 has two threads:

- **Thread A — honor `@Service(javaServiceLoaderCompatibilityMode)` (gap G3).** Today JVM
  `META-INF/services` is always generated as if `NO_COMPATIBILITY` (the impl map is keyed by the
  `X$ServiceProvider` nested class). The runtime JVM loader (`ServiceLoader.jvm.kt:34`) already
  branches on all three modes — M2 makes resource **generation** match, and adds a FIR checker
  constraint for `FULL_COMPATIBILITY`.
- **Thread C — the JVM-test DSL** (deferred from M1c). Build a `JvmScenario` DSL, factor compat
  assertions into shared helpers usable by both the JVM and KMP scenarios, add a jvm-only test hook
  to `KmpScenario`, and migrate the existing ad-hoc pure-JVM tests onto the new DSL.

**Deferred:** thread B — `ServiceLoaderUsagesCollector` — is **moved to M5**. Its only consumer is
the `module-info.properties` `# uses` section, which M5 wires up; it also carries the uncertain
reified-`inline` extraction risk. The stub in `ir/Collectors.kt` stays as-is until M5.

## Non-goals

- `module-info.java` generation / wiring (M5).
- `@JvmProvides` checker + IR (M3).
- R8-friendly `ServiceLoader.load` rewrite (M4).
- Any generated adapter/`provider()`-method glue to make **objects, functions, or properties**
  loadable under `FULL_COMPATIBILITY` (roadmap: "without extra glue"). The one piece of synthesis we
  *do* allow is a no-arg constructor for an all-default-param **class** (see §A2).
- Changes to the frozen runtime public API.

---

## Thread A — compat-mode generation

### A1. The model

The compat enum is purely a `java.util.ServiceLoader` (JVM) concern. On klib / native / js,
registration is via the `@EagerInitialization` registry regardless of mode, so **all mode logic
lives inside the `is JvmSweetIrPluginContext` branch** of
`ServiceProviderImplementationGenerator.process()`. The klib branch is untouched.

Per service, the JVM branch reads the mode from the resolved service class's `@Service` annotation
(default `NO_COMPATIBILITY` when the argument is absent) and emits:

| Mode | `META-INF/services` file | Entries listed | Wrapper impl generated? |
|---|---|---|---|
| `NO_COMPATIBILITY` | `<pkg>.X$ServiceProvider` | generated wrapper impl classes (`ServiceProvider<T>` subclasses) | yes *(current behavior)* |
| `PROVIDE_COMPATIBILITY` | `<pkg>.X$ServiceProvider` | generated wrapper impls — **identical generation to `NO`** | yes |
| `FULL_COMPATIBILITY` | `<pkg>.X` | the user `@Provides` **class itself**, directly | **no** (skipped — see §A2) |

`PROVIDE_COMPATIBILITY` generates exactly like `NO_COMPATIBILITY`: a Kotlin `@Provides` writes only
to `X$ServiceProvider`. The `META-INF/services/X` file is left for **Java** authors to hand-register
their own providers; sweetspi's runtime `PROVIDE` path (`loadKotlinSequence(X$ServiceProvider) +
loadJavaSequence(X)`) scans both and finds each provider once — no duplicates. This means the
`FULL` constraint (§A3) does **not** apply to `PROVIDE`; a Kotlin `@Provides` keeps every declaration
form under `PROVIDE`.

`FULL_COMPATIBILITY` lists the user impl class directly so plain `java.util.ServiceLoader.load(X)`
instantiates it. sweetspi's runtime `FULL` path also goes through `loadJavaSequence(X)`, never
touching `X$ServiceProvider`.

The data flow is unchanged in shape: `ServiceProviderImplementationGenerator.generate()` still
returns `Map<ClassId, Set<ClassId>>` (jsl-service-id → impl-ids). Only *which* `ClassId` is keyed
(the `X$ServiceProvider` vs `X`) and *which* impl ids are the values (wrapper vs user class) differ
per mode. `generateJvmServices` / `generateModuleInfoProperties` and `toJvmString()` need no change.

### A2. `FULL`-only generation specifics (JVM)

For a `FULL` service, in the JVM branch:

1. **Skip building the `ServiceProvider` wrapper class.** Under `FULL` the runtime instantiates `X`
   directly and wraps it in `InstanceProviderWrapper` (`ServiceLoader.jvm.kt:80`); the generated
   wrapper is never referenced, so emitting it would be dead bytecode.
2. **List the user `@Provides` class's `ClassId` directly** as the impl id for service `X`.
3. **Synthesize a public no-arg constructor when needed.** Every valid `@Provides` class has a
   no-arg or all-default-param constructor (enforced by the existing checker). Java's `ServiceLoader`
   requires a *public no-arg* `<init>()`, which Kotlin does **not** emit for an all-default-param
   constructor (only the `DefaultConstructorMarker` overload exists). So when the class has no real
   no-arg constructor, generate a public no-arg constructor that delegates to the existing one with
   all parameters defaulted. This synthesis is **JVM-`FULL`-only**.

### A3. Checker constraint (FIR)

A new rule in `fir/ProvidesChecker.kt`: for each provided service type, resolve the service symbol,
read its `@Service(javaServiceLoaderCompatibilityMode = …)` mode (**including cross-module**, when
the service lives in a dependency), and **only for `FULL_COMPATIBILITY`** require the `@Provides`
target to be a **non-object class**:

- `object`, top-level/object `fun`, and `val`/property providers → **error** (no nameable
  JSL-instantiable class; wrapper glue is out of scope for `FULL`).
- A non-object `class` is always acceptable: it already has a no-arg or all-default-param constructor
  (general `@Provides` rule), and §A2 synthesizes the public no-arg ctor when only all-default exists.

The diagnostic is added to `fir/SweetDiagnostics.kt` using the `by error*<KtElement>()` DSL in the
`KtDiagnosticsContainer` (consistent with existing custom diagnostics). The checker is
`MppCheckerKind.Common`, so it fires for multiplatform `commonMain` too — the flag is therefore
checkable in multiplatform projects, not only pure-JVM ones.

Reading the mode happens in two places — the **FIR checker** (for the constraint) and the **IR JVM
branch** (for generation). Both have the resolved service class in hand.

---

## Thread C — JVM-test DSL

### C1. `api/JvmScenario.kt`

A single-target JVM project-spec DSL, mirroring `api/KmpScenario.kt` but simpler (one target, no
matrix):

- `module(name, dependsOn)` for multi-module projects (e.g. service in a library, provider/consumer
  in downstream modules — the cross-module "library" promise on JVM).
- Kotlin `main` / `test` source writers, plus a **Java source** writer and a **plain
  `java.util.ServiceLoader`** consumer runner (needed to prove `FULL` loads via stock JSL, from both
  Kotlin and Java callers).
- Two runners mirroring the KMP side:
  - `jvmCheckerScenario(code) { failsWith(msg) | compiles() }` — compile-only, assert the diagnostic.
  - `jvmExecutionScenario { … }` — build + run the consumer test, assert runtime discovery.

### C2. `api/CompatAssertions.kt` (shared)

Compat-specific assertions, called by **both** `JvmScenario` and `KmpScenario`:

- `assertServicesFile(target, serviceId, expectedImplIds)` — read the generated
  `META-INF/services/<serviceId>` from the build output and assert its contents match the expected
  entries for the mode under test.
- a helper for loading a service via plain `java.util.ServiceLoader` in generated test code.

### C3. `KmpScenario` jvm-only test hook

Add a jvm-only test source set runner (e.g. `jvmTest(body)`, runs only on the `Jvm` target) so an
MPP scenario can exercise the jvm-target compat behavior — in particular load a `FULL` service via
plain `java.util.ServiceLoader` on the jvm target of a multiplatform project.

### C4. Migration

Re-express the existing ad-hoc pure-JVM tests onto the new DSL, preserving their assertions and only
unifying the bootstrap:

- `CheckerTest` → `jvmCheckerScenario`.
- `ProviderFormsTest.testJvm` → `jvmExecutionScenario`.
- `MultiModuleServiceTest` (jvm) → `jvmExecutionScenario` with modules.

---

## Testing & exit criteria

- **Per-mode functional tests** (`JvmScenario`): one each for `NO` / `PROVIDE` / `FULL`, asserting
  generated `META-INF/services` contents (shared assertion) **and** runtime discovery via sweetspi
  `ServiceLoader.load`.
- **`FULL` Java-loadability**: a plain `java.util.ServiceLoader` consumer (a Kotlin caller and a Java
  source consumer) loads the service.
- **`FULL` constructor synthesis**: an execution test where the `@Provides` class has only an
  all-default-param constructor and is loaded by plain JSL (proves the synthesized no-arg ctor).
- **Checker tests**: `FULL` + object/function/property → new diagnostic; `FULL` + non-object class
  (no-arg and all-default-param variants) → compiles; `NO`/`PROVIDE` + every declaration form →
  compiles (unchanged); **cross-module** `FULL` service declared in a dependency with a bad provider
  downstream → error.
- **MPP smoke** (`KmpScenario`): `@Service(FULL)` + an instantiable-class provider in `commonMain`;
  a jvm-only test loads it via plain `java.util.ServiceLoader`.
- Migrated JVM tests stay green; full `:sweetspi-tests:test` is green.
- `./gradlew :sweetspi-compiler-plugin:compileKotlin` succeeds with `-Werror`.

---

## Risks / verification items

Verify uncertain compiler APIs via the **IDEA MCP server** (`search_symbol` / `read_file`,
`projectPath=/Users/Oleg.Yukhnevich/Projects/whyoleg/sweet-spi`) — never unzip jars.

1. **Read the enum annotation argument in IR** (JVM branch): confirm the shape (expected
   `IrGetEnumValue` → `symbol.owner.name`) and the absent-argument default (`NO_COMPATIBILITY`).
2. **Read the cross-module `@Service` enum argument in the FIR checker**: confirm resolved-symbol
   annotation-argument access works for a service in a dependency. Note the M1 caveat that
   `symbol.hasAnnotation` can throw *during generation*; checkers run post-resolution, so reading
   resolved annotation arguments should be safe — verify.
3. **Synthesize a public no-arg constructor in IR** that delegates to an all-default-param
   constructor: confirm how to emit the delegating constructor call relying on default parameters
   (likely via the synthetic `<init>$default` stub). **Fallback** if infeasible: make the §A3 checker
   reject all-default-param classes under `FULL` and require an explicit no-arg constructor — revisit
   with the user before adopting the fallback.
4. **Skipping the wrapper on JVM-`FULL`**: confirm nothing else references the generated wrapper on
   the JVM-`FULL` path.
5. **Compiling a Java consumer** in the JVM test project: confirm the test harness can add and
   compile a Java source file alongside Kotlin (`java` plugin / `withJava` interplay).

---

## Roadmap doc updates (`2026-06-02-roadmap-design.md`)

- Move `ServiceLoaderUsagesCollector` from M2 to **M5** (its only consumer is `module-info`'s
  `# uses`).
- Restate M2 as *compat enum honored in IR/resources + JVM-test DSL*; update the M2 exit criteria to
  match this spec.
- Resolve the M1c "JVM-test DSL deferred to M2" note.

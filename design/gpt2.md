# Compile-Time Service Loading and Discovery in Kotlin Multiplatform

Perfect. I’ll explore practical and scalable ways to introduce compile-time service discovery into Kotlin Multiplatform, considering the
full range of possibilities—from standard library APIs and Gradle plugin enhancements to compiler plugins and potential language syntax
additions.

I’ll evaluate options that would work consistently across all supported KMP targets (JVM, Native, JS, Wasm), support a wide range of use
cases (plugin systems, DI, modular architecture), and feel native to Kotlin users and tooling. I’ll get back to you with a breakdown of
viable approaches, their trade-offs, and what would need to change (if anything) in the language, compiler, or tooling ecosystem.

# Compile-Time Service Loading and Discovery in Kotlin Multiplatform

**Introduction**  
Kotlin Multiplatform (KMP) currently lacks a built-in equivalent to Java’s `ServiceLoader` for discovering implementations of an interface
across module boundaries. On JVM, service loading is often done via runtime resources and reflection (e.g. reading `META-INF/services`), but
this doesn’t translate well to Native, JavaScript, or Wasm
targets ([How do I configure the engine for KTOR HTTPclient Multiplatform Kotlin projects? Addition of SSL certificate is also required - Stack Overflow](https://stackoverflow.com/questions/59229189/how-do-i-configure-the-engine-for-ktor-httpclient-multiplatform-kotlin-projects#:~:text=Not%20possible%20to%20add%20Apache%2C,Also)).
Instead, KMP needs a compile-time mechanism to register and discover “services” (implementations of a common interface) so that plugin
systems, modular components, and dependency injection (DI) frameworks can work uniformly on all platforms. This report explores several
approaches – from library-based solutions to compiler plugins and potential language features – evaluating their feasibility, developer
experience, performance impact, and prospects for standardization.

**Requirements and Challenges**  
Any solution for KMP service discovery must meet certain requirements and overcome platform limitations:

- **Multiplatform Support:** It should work on **all** Kotlin targets (JVM, Android, Native, JS, Wasm, etc.) in a uniform way. Many
  platforms (Native, Wasm, JS) lack reflection or dynamic classloading, so the Java approach isn’t directly
  usable ([kotlinx-spi/ReadMe.md at master · sellmair/kotlinx-spi · GitHub](https://github.com/sellmair/kotlinx-spi/blob/master/ReadMe.md#:~:text=Java%27s%20ServiceLoader%20is%20a%20pretty,however%2C%20have%20the%20following%20problems)).
- **Compile-Time Resolution:** Services should be registered at compile time (or build time) rather than discovered via runtime reflection
  or I/O. This avoids runtime overhead and ensures compatibility with ahead-of-time compiled
  platforms ([kotlinx-spi/ReadMe.md at master · sellmair/kotlinx-spi · GitHub](https://github.com/sellmair/kotlinx-spi/blob/master/ReadMe.md#:~:text=,Reflection%20might%20be%20unsafe)). (
  Reflection is not only unsupported on Native but can also be undesirable or unsafe in some
  contexts ([kotlinx-spi/ReadMe.md at master · sellmair/kotlinx-spi · GitHub](https://github.com/sellmair/kotlinx-spi/blob/master/ReadMe.md#:~:text=,Reflection%20might%20be%20unsafe)).)
- **Dynamic Extensibility:** The mechanism should allow implementations to reside in separate modules or libraries (even third-party
  plugins) that the main application can discover without manual wiring. In other words, the service interface’s implementations won’t all
  be visible in one compilation unit, which is exactly why a discovery mechanism is needed.
- **No Custom Tooling Requirements:** Ideally, it should work out-of-the-box with the standard Kotlin Gradle plugin and existing
  infrastructure. Requiring exotic build steps or custom compilers would hinder broad adoption. The solution should leverage existing
  tools (annotations, Gradle, the compiler plugin system, etc.) in a familiar way.
- **Efficiency:** It must not excessively bloat the binary or slow down build times. Keeping everything static at compile time can actually
  **improve runtime performance** (no reflection scanning) at the cost of a bit more compile-time work. The design should minimize that
  overhead and allow dead-code elimination to remove unused services when possible.
- **Opt-in and Ergonomic:** Both library authors (who provide services) and application developers (who consume them) should find the
  solution easy to use. It should be opt-in – only those who need service discovery pay the cost – and the usage should feel idiomatic to
  Kotlin (leveraging features like annotations, `object` singletons, etc., rather than clunky configuration).

Below we discuss four categories of solutions, categorized by their level of integration: **(1)** library-based code generation, **(2)**
compiler plugins (including KSP-based and IR transformations), **(3)** Gradle build-time approaches, and **(4)** new language or standard
library features. Each approach is explained with its mechanics, examples, pros/cons, and how it meets the above requirements.

## 1. **Library-Based Solution with Annotations and Code Generation**

One straightforward approach is to use a **library + annotation processor** (such as Kotlin Symbol Processing, KSP) to collect service
implementations at compile time and generate a registry class. Library authors annotate their service implementations, and the annotation
processor generates code (in the common or platform source sets) that lists or registers all such implementations. At runtime, the library
provides an API to retrieve all registered services of a given type – mimicking `ServiceLoader.load()` but without reflection.

**How it Works:** Suppose you have a service interface `Plugin` in common code. A library-based solution would define an annotation (e.g.
`@Service` or `@ServiceProvider`) that service implementers apply to their classes. A KSP processor then finds all classes annotated with
`@Service` and generates code to include them in a registry. For example, it might generate an `object PluginRegistry` with a
`val services: List<Plugin>` that contains an instance of each implementation. The application can then simply use this registry (or call a
generated `ServiceLoader.load<Plugin>()` function) to get all available implementations.

**Example – Sweet SPI:** The *Sweet**SPI*** library by Whyoleg demonstrates this approach for KMP. It provides two annotations (`@Service`
for interfaces and `@ServiceProvider` for implementations) and a tiny runtime API. Its KSP processor automatically generates the “glue” code
for each target, and a Gradle plugin streamlines the
setup ([GitHub - whyoleg/sweet-spi: Simple SPI (Service Provider Interface) for Kotlin Multiplatform (equivalent of JVM's Service Loader)](https://github.com/whyoleg/sweet-spi#:~:text=,setup%20of%20KSP%20and%20runtime)).
As a result, consumers just depend on the library and call `ServiceLoader.load<MyInterface>()` – all implementations across modules are
discovered **without any reflection
** ([GitHub - whyoleg/sweet-spi: Simple SPI (Service Provider Interface) for Kotlin Multiplatform (equivalent of JVM's Service Loader)](https://github.com/whyoleg/sweet-spi#:~:text=,which%20is%20available%20at%20runtime)).
SweetSPI supports *all 24 Kotlin targets* with this
method ([GitHub - whyoleg/sweet-spi: Simple SPI (Service Provider Interface) for Kotlin Multiplatform (equivalent of JVM's Service Loader)](https://github.com/whyoleg/sweet-spi#:~:text=,setup%20of%20KSP%20and%20runtime)),
showing that a well-designed library + codegen can cover JVM, Native, JS, Wasm, etc., uniformly.

**Example – KRouter SPI:** KRouter (a multiplatform routing library) recently added a similar SPI mechanism using KSP. Developers mark
classes with `@Service` and at compile time KRouter’s processor collects those classes so that `KRouter.getServices<HtmlParser>()` returns
all implementations at
runtime ([KRouter Now Supports Kotlin Multiplatform SPI Mechanism | by ZhangKe | Medium](https://medium.com/@kezhang404/krouter-now-supports-kotlin-multiplatform-spi-mechanism-8d1348436430#:~:text=%2F%2F%20Application%20fun%20main%28%29%20,HtmlParser%3E%28%29)).
As the KRouter team notes, “Kotlin Multiplatform does not inherently provide such capabilities, so we need to devise our own
solution” ([KRouter Now Supports Kotlin Multiplatform SPI Mechanism | by ZhangKe | Medium](https://medium.com/@kezhang404/krouter-now-supports-kotlin-multiplatform-spi-mechanism-8d1348436430#:~:text=The%20above%20method%20leverages%20Java%E2%80%99s,to%20devise%20our%20own%20solution)) –
in this case, a library-based code generation approach.

**Pros (Library + Codegen):**

- *Works with today’s tools:* This approach can be implemented using the standard Kotlin Gradle plugin plus KSP – no custom Kotlin compiler
  needed. It’s purely a library and an annotation processor, which is familiar to many developers (similar to how Dagger or Moshi codegen
  works on JVM).
- *Multiplatform by design:* KSP supports multiplatform projects, and the generated code can use conditional logic or expect/actual to
  accommodate platform differences if needed. In practice, libraries like SweetSPI show that one setup can target all platforms
  uniformly ([GitHub - whyoleg/sweet-spi: Simple SPI (Service Provider Interface) for Kotlin Multiplatform (equivalent of JVM's Service Loader)](https://github.com/whyoleg/sweet-spi#:~:text=,setup%20of%20KSP%20and%20runtime)).
- *No runtime reflection:* All discovery is done at compile-time. The resulting runtime behavior is just iterating over a pre-populated list
  or calling pre-registered singletons. This is efficient and avoids the need for dynamic class loading (important for Native/Wasm where
  such mechanisms don’t exist).
- *Automatic and modular:* Service implementers simply annotate their classes; they don’t need to manually register with a central registry.
  Implementations can live in any module or library – if the module is on the classpath at build/runtime, it will be included in the
  generated
  registry ([GitHub - whyoleg/sweet-spi: Simple SPI (Service Provider Interface) for Kotlin Multiplatform (equivalent of JVM's Service Loader)](https://github.com/whyoleg/sweet-spi#:~:text=,which%20is%20available%20at%20runtime)).
  This supports plugin architectures where plugins are developed independently.
- *Consumer simplicity:* Consumers of the service just call an API to get the implementations. For example, SweetSPI’s
  `ServiceLoader.load<SimpleService>()` will automatically load providers from any module on the
  classpath ([GitHub - whyoleg/sweet-spi: Simple SPI (Service Provider Interface) for Kotlin Multiplatform (equivalent of JVM's Service Loader)](https://github.com/whyoleg/sweet-spi#:~:text=%2F%2F%20module%3A%20A%2C%20B%2C%20C,service.saySomethingSweet%28%29%20%7D)) ([GitHub - whyoleg/sweet-spi: Simple SPI (Service Provider Interface) for Kotlin Multiplatform (equivalent of JVM's Service Loader)](https://github.com/whyoleg/sweet-spi#:~:text=%2A%20Easy,multiple%20services%20via%20one%20declaration)).
  No additional setup is needed in the consuming code beyond depending on the
  library ([GitHub - whyoleg/sweet-spi: Simple SPI (Service Provider Interface) for Kotlin Multiplatform (equivalent of JVM's Service Loader)](https://github.com/whyoleg/sweet-spi#:~:text=,which%20is%20available%20at%20runtime)).
  This feels very much like a natural extension of Kotlin/JVM’s ServiceLoader to KMP.
- *Opt-in and flexible:* Only projects that apply the annotation and processor are affected. If you don’t use it, nothing is added to your
  build. Libraries can choose to use this mechanism internally without affecting others, aside from the requirement that consumers include
  the runtime library to use the loading API.

**Cons (Library + Codegen):**

- *Build-time overhead:* Running an annotation processor (KSP) adds compilation work. It has to scan for service annotations and generate
  code. However, KSP is relatively efficient and incremental. The overhead is usually minor (comparable to other common codegen tasks), but
  in very large projects with many services it could impact build times.
- *Added complexity for authors:* Library authors must include the annotation definitions and set up KSP. Tools like a Gradle plugin can
  automate this configuration, but it’s another moving part. Also, each such library might introduce its own annotation – without
  coordination, there could be multiple “SPI” annotations in the ecosystem.
- *Generated code size:* The approach typically generates code that explicitly references all implementations. This ensures the linker
  retains them on Native and Wasm (preventing dead-strip), but it also means they contribute to the binary size. In practice this overhead
  is small (just a list of class instantiations or references), but it’s something to consider if hundreds of services are involved. Dead
  code elimination can still remove entire service implementations you don’t use at all (e.g., if no one ever calls the loader for a
  particular service interface, the list and its items might be pruned).
- *KSP support on all targets:* As of Kotlin 1.9+, KSP works for JVM, Android, Native, and JS IR. Support for new targets like Wasm should
  arrive with the Kotlin 2.0/K2 tooling. Assuming KSP (or a similar codegen tool) keeps pace, this method remains viable. However, if
  there’s a lag (e.g. early Kotlin/Wasm might not support annotation processing), that could temporarily block usage on that new target.
- *Lack of standardization:* Each library might implement its own variant. Until a de-facto or de jure standard emerges, there’s a risk of
  fragmentation – one library’s service annotations might not be recognized by another’s loader. This is mainly an ecosystem concern; it can
  be mitigated if a popular library (or an official one) is widely adopted as the convention.

**Developer Experience:** For most developers, the library+KSP solution is quite friendly. A service provider author adds an `@Service`
annotation to their class (and ensures the module applies the KSP plugin or a Gradle plugin that includes it). This is very similar to using
Dagger’s `@Module` or `@Provides` in JVM projects. The rest is automatic – the codegen will produce the necessary wiring. From the
application’s perspective, using the service is one line of code to fetch the list. No platform-specific code is needed to handle different
targets. Importantly, this approach is entirely opt-in: if you depend on a library that uses it internally, you might not even realize it –
things just work out-of-the-box. If you want to adopt it in your own codebase, you add the annotations and processor (which can often be
done by including a single Gradle dependency or plugin). Overall, this approach scores well on ergonomics, as evidenced by its successful
use in libraries like SweetSPI (which requires “no additional setup… except depending on the
library” ([GitHub - whyoleg/sweet-spi: Simple SPI (Service Provider Interface) for Kotlin Multiplatform (equivalent of JVM's Service Loader)](https://github.com/whyoleg/sweet-spi#:~:text=,which%20is%20available%20at%20runtime))).

## 2. **Compiler Plugin Solutions (KSP or IR Transformation)**

Another category of solutions involves writing a **compiler plugin** that handles service discovery. KSP itself is actually implemented as a
compiler plugin (focused on processing source symbols), but here we consider going further – using either KSP in a more advanced way or a
lower-level IR plugin that directly modifies the compiled program. A compiler plugin can automate service registration without relying on
separate generated source files, potentially making the mechanism even more seamless. This could be a third-party plugin or (eventually) an
official one.

**Using KSP as an Aggregator:** One way to view this is simply an extension of Approach 1 – KSP can act as a centralized aggregator if
configured at the application level. For example, an **aggregating KSP processor** could run in the final compilation of the app, gather
service classes from all modules (perhaps by reading their metadata or using dependency injection), and generate the registry. In practice,
though, KSP operates per module, so a more typical use is the library-oriented approach already described. Thus, we focus on IR compiler
plugins below for a distinct perspective.

**IR Compiler Plugin Approach:** A Kotlin compiler plugin (for the IR backend) can inspect and alter the abstract syntax tree / IR during
compilation. We could design a plugin that recognizes service interfaces and implementations (perhaps via annotations or conventions) and
injects code to register those implementations. For instance, the plugin might generate a hidden function that gets all `Plugin`
implementations and call it from `main()` or replace calls to a `ServiceLoader.load<Plugin>()` with a compile-time constructed list. This
would achieve the same end result (a list of implementations) but without explicit codegen steps that developers see.

**Prototype – kotlinx.spi:** A proof-of-concept project called **kotlinx.spi** illustrates what a dedicated compiler plugin might look like.
In this prototype, all service loading is “fully static” – *“All services will be known at compile time; the corresponding IR or ByteCode
will be generated to link
them.”* ([kotlinx-spi/ReadMe.md at master · sellmair/kotlinx-spi · GitHub](https://github.com/sellmair/kotlinx-spi/blob/master/ReadMe.md#:~:text=This%20spi%20implementation%2C%20whilst%20also,be%20generated%20to%20link%20them)).
The design uses a special runtime module that is essentially a stub; when you apply the custom plugin, the compiler replaces that stub with
generated code that knows about the actual services. This plugin even sets up an extra compile task for each target of the project to gather
services from the classpath and produce the real
registry ([kotlinx-spi/ReadMe.md at master · sellmair/kotlinx-spi · GitHub](https://github.com/sellmair/kotlinx-spi/blob/master/ReadMe.md#:~:text=Code%20gets%20compiled%20against%20the,kotlinx.spi.runtime)).
The result is that when the app runs, it already has all service implementations linked in, with no reflection or scanning needed. This
approach worked across JVM and Native in the demo, but it required a small tweak to the Kotlin/Native compiler’s library resolver to allow
swapping out the stub
runtime ([kotlinx-spi/ReadMe.md at master · sellmair/kotlinx-spi · GitHub](https://github.com/sellmair/kotlinx-spi/blob/master/ReadMe.md#:~:text=The%20Kotlin%2FNative%20implementation%20requires%20one,See%20the%20commit%20in%20kotlin%2Fsellmair%2Fspi)) (
i.e., some low-level integration).

**Pros (Compiler Plugin):**

- *Transparent integration:* A well-implemented compiler plugin could make service discovery feel like a built-in language feature. For
  example, simply annotating a class could be enough – the plugin takes care of producing the registry behind the scenes. The user might not
  see any generated source; it’s all done within the compiler’s IR. This reduces boilerplate in the repository (no extra files to manage).
- *Powerful and flexible:* IR plugins have a full view of the program’s intermediate representation. They can implement complex logic –
  e.g., linking across modules, or even conditional inclusion of services. They aren’t limited by the source-level API. For instance, a
  plugin could intercept the usage of a specific API (like calls to a `loadServices()` function) and replace them with optimized code. This
  could let library authors use a common API that gets “magically” resolved by the plugin at compile time.
- *Potential for official support:* If the Kotlin team or community deems this functionality important, a standardized compiler plugin might
  emerge (perhaps as part of kotlinx libraries or even the language). An official plugin would ensure broad adoption – similar to how
  `kotlinx.serialization` uses a compiler plugin to auto-generate serializers. This would eliminate the fragmentation issue; everyone would
  rely on the same mechanism for SPI.
- *No runtime cost:* Like the library approach, everything is resolved ahead-of-time. In fact, an IR plugin can be even more optimal by
  injecting code directly where needed (possibly avoiding even the overhead of constructing a list if not necessary). It also naturally
  works on all platforms since the IR compiler runs for each target. The compile-time work might be more centralized here – e.g., done in
  the final binary production – which could enable global optimizations.
- *Minimal consumer code:* In an ideal form, a compiler plugin might allow you to *not even write explicit loading code*. For example, it
  could automatically call initializers to register services. However, this depends on design – most likely you’d still call a function to
  retrieve services, but that function’s implementation is provided by the plugin. Either way, the amount of code a developer writes is
  small.

**Cons (Compiler Plugin):**

- *Complexity and maintenance:* Developing a custom compiler plugin (especially one that works with multiplatform targets) is non-trivial.
  The plugin must interface with Kotlin’s compilation pipeline for each target (JVM, Native, JS, etc.), which can involve understanding IR,
  metadata, and in some cases linker details. Such plugins often need to be updated for each new Kotlin version. This is a higher barrier to
  entry compared to writing a KSP processor.
- *Adoption hurdles:* Until it’s official, asking users to apply a compiler plugin (beyond the standard ones) can be a tough sell. Unlike a
  library dependency, a compiler plugin might raise concerns about stability and compatibility. For broad adoption, either a big player (
  JetBrains or a major library) needs to champion it, or it needs to prove itself over time.
- *Potential Gradle integration needed:* Some scenarios require coordination with Gradle tasks. For example, the kotlinx.spi approach
  introduced new compile tasks (`compileKotlinSpiJvmMain` etc.) to generate the
  registry ([kotlinx-spi/ReadMe.md at master · sellmair/kotlinx-spi · GitHub](https://github.com/sellmair/kotlinx-spi/blob/master/ReadMe.md#:~:text=Code%20gets%20compiled%20against%20the,kotlinx.spi.runtime)).
  This kind of wiring can be done via a Gradle plugin that accompanies the compiler plugin. It adds to the setup complexity, though tools
  can hide it from the user (much like applying the Kotlin plugin automatically sets up source sets). Still, it’s an additional piece that
  must work correctly on all platforms.
- *Limitations without language support:* A third-party IR plugin can’t easily break out of certain constraints. For instance, the need to
  modify Kotlin/Native’s linking (to ensure service info isn’t stripped, or to inject the “real” registry code) might not be fully solvable
  without compiler
  changes ([kotlinx-spi/ReadMe.md at master · sellmair/kotlinx-spi · GitHub](https://github.com/sellmair/kotlinx-spi/blob/master/ReadMe.md#:~:text=The%20Kotlin%2FNative%20implementation%20requires%20one,See%20the%20commit%20in%20kotlin%2Fsellmair%2Fspi)).
  In other words, a user-space plugin might hit walls that require deeper Kotlin compiler support. This was evident in the prototype
  requiring a custom compiler build for K/N.
- *Incremental build concerns:* If the plugin gathers information across modules, it needs to do so incrementally. There’s a risk that
  changes in one module (adding a new service) might not trigger re-compilation of the consumer in an out-of-the-box way. A well-designed
  plugin/Gradle integration can manage this (by declaring dependencies between tasks), but it’s another thing that must be handled carefully
  to avoid stale registries or unnecessarily full rebuilds.

**Developer Experience:** When polished, a compiler plugin solution could be very ergonomic. Imagine simply adding an official `kotlinx.spi`
plugin to your Gradle configuration and then using a standard `@Service` annotation. For library authors, that’s essentially the same effort
as adding an annotation today, but they might not even need to depend on an external SPI library – the annotation could be provided by the
plugin’s API. For application developers, if the plugin automatically makes all services available, they might only need to call a standard
function like `ServiceLoader.load<MyInterface>()` (or perhaps an even more Kotlin-idiomatic sequence). In the best case, it “just works”
with minimal ceremony. The current reality, however, is that without official support, using a custom compiler plugin means extra Gradle
configuration and trusting a third-party with low-level codegen in your build. That’s why, as of now, the library+KSP approach is more
prevalent. But if the compiler plugin became standardized or bundled (hypothetically, **Kotlin 2.x** could introduce a built-in SPI
mechanism), the DX would become very smooth and could be made opt-in via a simple Gradle flag or plugin application. In summary, compiler
plugins have high potential for DX, but only once the integration overhead is solved and the community is on board.

## 3. **Gradle Plugin and Metadata-Based Discovery**

This approach shifts the focus to the build system: use Gradle (or another build tool) to orchestrate service discovery **outside of the
compiler**. Instead of (or in addition to) annotation processing, a Gradle plugin could analyze the outputs or metadata of each module and
generate the service registry. Essentially, Gradle would act as the “meta-processor” that knows about all modules in the project and ties
them together.

**How it Might Work:** There are a few variants of this idea:

- **Resource file aggregation:** Each library module that provides a service could include a resource file (similar to `META-INF/services`
  on JVM) declaring its implementations. This could even be done with a simple text file or a Kotlin metadata file produced during
  compilation (perhaps via a small KSP in the library). A Gradle plugin in the application would then merge these files (since it has access
  to all dependencies on the classpath) and either produce a combined resource that the runtime can read or generate a Kotlin class with all
  implementations. This is analogous to how some Java build tools aggregate services for uber-JARs, except it must handle K/N and JS as
  well.

- **Classpath scanning at build time:** Gradle (via a plugin) can inspect the compiled classes or artifacts of dependencies. For JVM
  artifacts (.jar), it could load or ASM-scan classes to find those that implement a certain interface or carry a certain annotation. For
  Kotlin/Native libraries (.klib), the plugin could use **kotlinx-metadata** to read the K/N metadata and find classes by annotation or
  supertype. Similarly, for Kotlin/JS, it could potentially parse the IR or use metadata. Once found, it can generate source code (or a
  resource) listing them. This generation would run as part of the build, producing a Kotlin source in the application module that is then
  compiled normally.

- **Gradle metadata or configuration:** Another angle is using Gradle’s module metadata or extension properties. For instance, a library
  author could configure a Gradle extension listing its service implementation FQNs. The application’s Gradle plugin could consume these and
  generate code. This is more manual and less elegant (it moves the list from code into build scripts), so it’s generally less desirable
  compared to automatic scanning.

A concrete example of build-time aggregation is actually seen in SweetSPI’s Gradle plugin. It simplifies setup by automatically applying KSP
where needed and ensures the generated code is available to
consumers ([GitHub - whyoleg/sweet-spi: Simple SPI (Service Provider Interface) for Kotlin Multiplatform (equivalent of JVM's Service Loader)](https://github.com/whyoleg/sweet-spi#:~:text=,setup%20of%20KSP%20and%20runtime)).
In essence, it’s coordinating the annotation processing across modules. Another example in a different domain is how Compose Multiplatform’s
Gradle plugin assists in code generation for UI – it’s not about service discovery, but it shows that Gradle can orchestrate multiplatform
codegen tasks across modules.

**Pros (Gradle-Orchestrated):**

- *Global awareness:* Gradle knows about all the modules and their outputs. This makes it well-positioned to aggregate information. Unlike a
  compiler (which typically works module by module), Gradle at the project level can gather data from multiple sources. This means it could
  collect service implementations even if they aren’t visible to each other or to the main compilation unit.
- *No changes to compiler needed:* All the heavy lifting is done outside the Kotlin compiler. We reuse the fact that each module’s artifacts
  contain the information (class files or metadata) about service classes. This avoids dealing with compiler internals or IR. It leverages
  the build system which is more high-level and easier to extend via plugins.
- *Flexible implementation:* The Gradle plugin can use any method to discover services – it could use annotation processing (KSP) under the
  hood, or it could use class scanning, or even require explicit registration. This means it can be iteratively improved or tailored. For
  example, on JVM it might rely on existing `META-INF/services` files (if libraries already generate those via `@AutoService` or similar),
  while on Native it might rely on an annotation and metadata scanning. The end result can be unified after gathering.
- *One-step setup:* If provided as a unified Gradle plugin, an application could apply something like
  `plugins { id("com.example.multiplatform-spi") }` and get everything configured. This plugin can automatically apply any needed
  configuration to subprojects (such as adding a KSP dependency for an annotation). It centralizes the configuration, so library authors
  just need to include an annotation (which the Gradle plugin could also provide). In short, it could be **zero-configuration** from the
  perspective of individual modules – all they do is annotate, and the Gradle plugin does the rest.
- *Possibility of code elimination:* Because the Gradle plugin could customize the generated registry per application, it might omit certain
  implementations if, for example, some modules aren’t actually included. This is similar to the compiler plugin’s advantage. Essentially,
  it can ensure you only pay for what you use (though any module on the classpath is assumed “used” to some extent).
- *Can leverage existing patterns:* Many platforms already have some notion of service descriptors (JVM service files, Android’s manifest
  registries, etc.). A Gradle approach can build on these. For instance, if using third-party JVM libs that already use `META-INF/services`,
  the plugin could read those files to integrate with the multiplatform registry, so you don’t have to double-annotate classes.

**Cons (Gradle-Orchestrated):**

- *Complex cross-platform logic:* Implementing the plugin to handle all target types is a significant effort. Parsing Kotlin/Native metadata
  or JS module descriptors is not trivial – the formats differ and may not be officially documented for such use. This approach might
  internally require some of the same work as a compiler plugin (understanding who implements what interface) but doing it at the build
  level with custom code. Maintaining that for new compiler versions could be burdensome.
- *Less built-in safety:* Because this is outside the compiler, you might lose some type-safety or get errors later in the pipeline. For
  example, if a class is mislabeled or if an expected annotation is missing, the Gradle plugin might silently omit it and you’d find out at
  runtime when a service is missing. A compiler plugin or KSP working on source could give a compile-time error or warning if something is
  configured incorrectly. That said, the Gradle plugin could inject checks (e.g., assert that at least one service was found for an
  interface, etc.), but it’s extra work.
- *Incremental build and caching:* If not designed carefully, scanning all dependencies on each build can be slow. Ideally, the plugin
  should hook into Gradle’s incremental build: when a module’s output changes, update that part of the registry. This requires integrating
  with Gradle’s task graph and cache. It’s doable (Gradle can treat the scanning as a task with inputs being the jar/klib files), but
  correctness is key. Otherwise, you might end up with stale data (missing a new service until a clean build) or always-forced execution (
  slower builds).
- *Developer insight:* With a build-time generated registry, developers might find it less obvious what’s happening. If something goes
  wrong, debugging is harder because the generation is a step removed. In contrast, with KSP you can inspect the generated source in each
  module to verify it’s correct. Here, the source might be generated in a build dir somewhere only for the final build. Good tooling or
  logging would be needed to make this transparent (e.g., outputting a summary of discovered services).
- *Coupling to Gradle:* A pure Gradle plugin solution inherently ties your build to Gradle more. KMP already assumes Gradle a lot of the
  time, but one advantage of KSP or compiler plugin is that they could, in theory, work with other build systems (Maven, Bazel) if
  integrated. A Gradle-specific implementation might not translate easily to other build setups, which could limit adoption for those not on
  Gradle. (However, given KMP’s primary tooling is Gradle, this may be a minor concern in practice.)

**Developer Experience:** If implemented well, a Gradle-based SPI could be very easy to use. Library authors would add an annotation (
provided by some runtime library or by the Gradle plugin’s API) like `@AutoService` or `@Service`. They might not even need to apply KSP
themselves – the top-level plugin could auto-configure it for them. Application developers would just apply the plugin and perhaps call a
standard loader function. The main difference from Approach 1 is that most of the “magic” happens at the application build phase instead of
during each library’s compilation. This means the feedback loop for library authors is slightly different – e.g., if they add a new service
class and run their library’s unit tests, the registration might not happen unless the Gradle plugin is invoked in that context. Tools can
mitigate this by also applying the plugin in library projects or by having a lightweight fallback (maybe a default no-op loader that returns
nothing if not assembled by the aggregator). These nuances aside, a Gradle approach can be made quite developer-friendly, as it centralizes
setup. It could even integrate with IDEs to generate the registry on the fly for running in the IDE. Overall, the DX can be comparable to
the annotation processor approach, with the potential benefit that one central plugin configures everything (so you don’t have to repeat KSP
setup in every module).

## 4. **New Language or Standard Library Features**

The most integrated (and long-term) option to consider is introducing a new language feature or an official standard mechanism in Kotlin
itself to support compile-time service discovery. This would elevate the SPI concept to a first-class citizen in Kotlin Multiplatform,
possibly making all the above approaches unnecessary for end users. This category could include new syntax, keywords, or standardized
annotations supported by the compiler/stdlib.

**Potential Language Features:**

- *Sealed or “open” interfaces across modules:* One idea is an extension of Kotlin’s sealed classes/interfaces. Normally, sealed types allow
  the compiler to know all subclasses **within the same module**. A new feature might allow marking an interface as a “service” which the
  compiler then treats specially, collecting implementations even from other modules. This would require the compiler to aggregate metadata
  from dependencies (perhaps using the module’s `.kotlin_metadata` information) and verify or list all implementations. It’s a bit like
  making an “open sealed” interface – open for extension in other modules, but with the compiler keeping track of implementations for
  certain purposes (like generating a registry or exhaustive when). This is not currently how Kotlin works, but it’s a conceivable language
  addition.
- *Standard annotations with compiler support:* Kotlin could introduce an annotation (for example, `@ServiceInterface` and
  `@ServiceImplementation`) in its standard library or in `kotlinx`. The Kotlin compiler (or a bundled plugin) would recognize these
  annotations and automatically generate the necessary code at compile time. This is similar to how `@Serializable` is handled by the
  kotlinx.serialization plugin. The benefit here is that it’s not new syntax, just a well-known annotation that has special compiler
  behavior when enabled.
- *Built-in registry or loader in stdlib:* The standard library could offer a multiplatform `ServiceLoader` equivalent. For instance,
  `kotlin.spi.ServiceLoader.load<T>()` could be a function that at runtime yields all `T` implementations. The compiler, knowing this is a
  special call, could replace its bytecode/IR with a precomputed list of T’s implementations (much like inlining). This way, it’s seamlessly
  integrated – developers call a standard API, and behind the scenes the compiler ensures it returns the right things on each platform. This
  might be implemented via a compiler plugin, but if it’s shipped with Kotlin, it feels like part of the language.

**Pros (Language-Level Solution):**

- *Official and consistent:* An officially supported feature would eliminate doubt and incompatibility. Everyone would use the same
  annotations/keywords, and the behavior would be well-documented and guaranteed by Kotlin. This greatly aids broad adoption – it becomes a
  normal part of KMP development if needed, rather than pulling in a third-party tool.
- *Better tooling support:* If the compiler is aware of services, IDEs could also provide assistance. For example, an IDE could warn if you
  have a `@ServiceInterface` with no implementations on the classpath, or it could navigate from an interface to its registered
  implementations even across module boundaries. This is possible because the information is an integrated part of the compile-time system,
  not hidden in generated code.
- *Performance and optimization:* Being built-in means the implementation can be highly optimized. The compiler can generate direct
  references to constructors or singletons of implementations without any reflection. On Native, it can ensure those are retained. On
  JS/Wasm, it can create an array of factories upfront. It might also allow tree-shaking of unused services if none of the code asks for
  them. Essentially, it can do everything approaches (1)-(3) do, but potentially more tightly coupled with the compiler’s own optimization
  pipeline.
- *Minimal user-facing complexity:* Ideally, new language support would mean users have less to configure. Perhaps no separate Gradle plugin
  or kapt/ksp dependency – just use the keywords. The threshold to use the feature could be as low as using `sealed` or `inline` – something
  any Kotlin developer can pick up. This also implies fewer things can go wrong: since it’s part of compilation, it either works or the
  compiler tells you why it can’t (e.g., error if two implementations have the same name in the same service, etc.).
- *Design for Kotlin idioms:* A built-in solution can leverage Kotlin language features elegantly. For example, it could require service
  implementations to be `object` singletons (which might simplify registration as they can be accessed directly), or allow services to be
  function interfaces (SAMs) and generate lambdas. It could also possibly integrate with `expect/actual` – maybe an
  `expect interface Service` could automatically combine actual implementations on each platform. There’s a lot of room to design something
  Kotlin-specific rather than mimicking Java’s model.

**Cons (Language-Level Solution):**

- *Requires Kotlin evolution:* Proposing and adding a language feature or even a new standard library component goes through a rigorous
  process. It would likely take significant time to design, implement, and stabilize. If this need is urgent for developers, waiting for a
  language change might not be practical.
- *Complexity in compiler:* While from the user side it’s simpler, the compiler team would have to implement multi-module service discovery,
  which is non-trivial. The compiler would have to aggregate information from dependency metadata (which it currently does for inlining and
  default args, but not for enumerating subclasses across modules). Ensuring this works with incremental compilation and the Gradle cache,
  and doesn’t slow down compilation, would be a challenge.
- *Language bloat:* Introducing new keywords or semantics just for service loading might be seen as overkill if library/plugin solutions
  suffice. Kotlin tries to avoid niche keywords – each addition should be justified by a broad need. Service discovery is important, but one
  could argue it can be handled at the library level (especially since not every project needs it). So there would be a high bar to clear to
  justify a new syntax.
- *Less flexibility for custom behavior:* A built-in mechanism might be somewhat rigid in how it works (to keep it simple). For instance, it
  might always instantiate classes via no-arg constructor, or always use singletons. Some use-cases might need more control (maybe a DI
  framework wants to create instances with parameters). Library or compiler-plugin approaches might be tailored for those scenarios, whereas
  a generic language feature might not cover all cases and thus not be used by more sophisticated frameworks. In other words, if it’s too
  generic it might be insufficient, and if it’s too specific it might not please everyone.
- *Transition and adoption:* If a language feature comes later, there will be an ecosystem transition period. Projects using their own
  solutions would consider migrating. It’s important that any official solution either interoperates with or clearly supersedes existing
  patterns. Otherwise, we might end up with legacy support issues or multiple mechanisms in use.

**Developer Experience:** If Kotlin introduced, say, a `@ServiceInterface` annotation in `kotlin.stdlib` and handled it automatically, the
developer experience would be excellent: define an interface, mark it, implement it in various modules, and call a function to get them.
Everything else would feel built-in. This is analogous to how `expect/actual` works – it’s just part of writing multiplatform code. Until
that happens (if ever), developers will either use one of the other approaches. It’s worth noting that even a language feature would likely
be opt-in (perhaps via an annotation or compiler option) because not every project wants the overhead. But from a user perspective, opt-in
could be as simple as adding `@ServiceInterface` to the interface definition – much simpler than adding and configuring a third-party tool.

## **Comparison of Approaches**

Each approach has its trade-offs, and they’re not mutually exclusive – a future official solution might combine aspects of these. Here’s a
quick comparison on key points:

- **Integration Level:** Library-based (Approach 1) is at the language’s periphery – it uses normal code generation facilities. Compiler
  plugin (Approach 2) delves into Kotlin’s compilation process for a deeper integration, offering potentially more magic at the cost of
  complexity. Gradle (Approach 3) leverages build tooling to tie things together without altering the compiler, acting as a middle ground.
  Language support (Approach 4) would bake the feature into Kotlin itself. There’s a spectrum here from least integrated (just a library) to
  most integrated (language change). More integration can yield a smoother experience but is harder to implement and standardize.

- **Feasibility Today:** The **annotation + KSP approach is feasible now and already used in real projects
  ** ([GitHub - whyoleg/sweet-spi: Simple SPI (Service Provider Interface) for Kotlin Multiplatform (equivalent of JVM's Service Loader)](https://github.com/whyoleg/sweet-spi#:~:text=,which%20is%20available%20at%20runtime)) ([KRouter Now Supports Kotlin Multiplatform SPI Mechanism | by ZhangKe | Medium](https://medium.com/@kezhang404/krouter-now-supports-kotlin-multiplatform-spi-mechanism-8d1348436430#:~:text=%2F%2F%20Application%20fun%20main%28%29%20,HtmlParser%3E%28%29)).
  It requires no changes to Kotlin and works with the current Gradle plugin. In contrast, the pure compiler plugin approach is mostly
  theoretical today – it exists in prototypes (kotlinx.spi) but isn’t widely available or stable. Gradle-based solutions are also feasible
  with some effort (SweetSPI’s plugin shows one way), though there isn’t a generic off-the-shelf Gradle SPI plugin yet. Language changes are
  the least immediately feasible; they’re a consideration for future Kotlin versions if the need is deemed common enough.

- **Uniform Multi-Platform Support:** All approaches are intended to support all targets, but how they achieve it differs. The library/KSP
  approach generates platform-specific adapters or uses common code to handle differences, and has been shown to cover all current
  targets ([GitHub - whyoleg/sweet-spi: Simple SPI (Service Provider Interface) for Kotlin Multiplatform (equivalent of JVM's Service Loader)](https://github.com/whyoleg/sweet-spi#:~:text=,setup%20of%20KSP%20and%20runtime)).
  The compiler plugin approach naturally works on all targets since it plugs into each backend’s IR (ensuring, for example, that Native gets
  the same treatment as JVM). Gradle approaches can handle all platforms by analyzing each artifact type, though that requires handling
  multiple formats. If Kotlin adds a language feature, by definition it would apply to all platforms the language supports. So in principle,
  all can be made uniform – it’s more a question of *ease*. Right now, library+KSP has the slight edge of being tested in multiplatform
  scenarios, whereas a custom IR plugin must specifically implement logic for each new backend (Wasm backend, for instance, would need
  support in the plugin when it becomes stable).

- **Performance:** All compile-time solutions aim to eliminate the runtime cost of discovery. Instead of scanning at startup, you pay the
  price at build time by generating a static list. This can *improve application startup and performance*, especially on platforms like
  Android where reflection based scanning can be
  slow ([WhatsNew 3.0 | Ktor Framework](https://ktor.io/changelog/3.0/#:~:text=WhatsNew%203.0%20,load%20calls%20%28like%20this%29)). The
  runtime performance of the final solution is roughly the same for all approaches – iterating over a pre-known list (which might be as
  simple as an array of objects or class references). The differences lie in build performance and binary size. KSP codegen adds some build
  time and a bit of code for each service; an IR plugin might add slightly less code (since it could reuse existing classes or inject
  directly) but the scanning has to happen somewhere (either in the plugin or via Gradle). In terms of binary size, all approaches include
  the implementations anyway, just maybe in different structures. It’s worth noting that these solutions solve the **dead code elimination**
  problem: if a service implementation is included as a dependency but nothing ever requests it, a smart linker could remove it. However, if
  your design is that *all* implementations of a service are always loaded, then by definition you’re keeping them all. Some DI frameworks
  might not always want *every* implementation; they might pick one by qualifier or some logic. In those cases, a compile-time discovery
  could still list them all but you’d choose at runtime among them. The overhead of a few extra class references that go unused is usually
  minimal, but it’s something to consider if targeting extremely small binaries (perhaps an embedded setting with Wasm).

- **Build and Tooling Impact:** Using a library+KSP is quite standard now; it plugs into Gradle’s KSP tasks which support incremental
  builds. Many developers are already using multiple KSP processors in projects (for JSON serialization, DI, etc.), so adding one more for
  service discovery is incremental. A custom compiler plugin might have more impact – it could slow down compilation if not optimized,
  especially if it has to scan a lot of classes in IR. But since it could leverage the compiler’s own knowledge of dependencies, it might
  not be worse than KSP. Gradle scanning might be the slowest if it has to open and read every dependency artifact – though caching and
  up-to-date checks can mitigate that in successive builds. In terms of IDEs, KSP-generated code is usually visible to IDEs (after one
  build, the generated sources are recognized), whereas compiler plugin generated code might be “invisible”. This could make debugging a bit
  tricky, but if it’s well-integrated (like how you don’t see generated code for Kotlin `@Serializable` classes, yet it works), developers
  don’t mind as long as it’s reliable.

- **Ergonomics and DX:** From a **library author’s perspective**, Approach 1 (library+KSP) means adding an annotation to their API (which is
  fine) and instructing users to apply a Gradle plugin or KSP. Approaches 2 and 4 (compiler or language) might not require the library
  author to ship any annotation at all if a standard one exists – they just document “mark your class with `@Service`”. That lowers the
  barrier for library maintainers. From an **application developer’s perspective**, all approaches can be made very easy: it comes down to
  including the right dependency or plugin and calling the loader. The differences are subtle – e.g., with KSP you might see a generated
  file in your project, whereas with a compiler plugin you don’t – but in usage, they call a function to get services. The **opt-in nature**
  is important: none of these should force you to have a service discovery mechanism if you don’t need one. All proposed solutions are
  opt-in (you have to add an annotation or enable a plugin explicitly). This is good for developer choice and keeping things lightweight.

- **Use-case Coverage:** A wide range of use cases are targeted: plugin architectures (where you might load all implementations of an
  extension point), module discovery (finding modules on startup that register themselves), and DI frameworks (where you might collect all
  bindings of a certain type). The compile-time approach generally covers these. One caveat: if a truly dynamic plugin system is needed (
  loading modules at runtime that were not known at compile time, e.g. loading a new .klib or .jar after deployment), then compile-time
  discovery can’t directly find those – you’d need a different mechanism (maybe a runtime registry update when the plugin is loaded). But
  for static modular designs (all components known at build time, which is typical in mobile and multi-platform scenarios), these solutions
  work. In fact, a static solution is often preferable for mobile/embedded where dynamic loading isn’t even feasible.

## **Feasible Options and Standardization**

**Current Best Practices:** As of 2025, the most **feasible and broadly applicable option is the library-based approach using annotations
and code generation**. This requires no special compiler support and has been proven by libraries like SweetSPI and KRouter’s SPI feature. A
likely path for developers today is to adopt one of those libraries or a similar solution: it delivers compile-time service loading with
relatively low effort. If you want a DIY approach, writing a custom KSP processor for your project’s needs is also an option (especially if
your needs are simple, e.g., just gather subclasses of a specific interface). KSP is stable and
multiplatform-capable ([GitHub - whyoleg/sweet-spi: Simple SPI (Service Provider Interface) for Kotlin Multiplatform (equivalent of JVM's Service Loader)](https://github.com/whyoleg/sweet-spi#:~:text=,setup%20of%20KSP%20and%20runtime)),
making it a solid choice for an “out-of-the-box” implementation.

**Emerging Solutions:** The community is actively experimenting with more integrated solutions. The kotlinx.spi prototype suggests that a *
*standardized compiler plugin** could become
reality ([kotlinx-spi/ReadMe.md at master · sellmair/kotlinx-spi · GitHub](https://github.com/sellmair/kotlinx-spi/blob/master/ReadMe.md#:~:text=This%20spi%20implementation%2C%20whilst%20also,be%20generated%20to%20link%20them)).
It’s not official yet, but it demonstrates that the Kotlin compiler *can* support fully static service discovery. If this or a similar
approach matures (perhaps via a Gradle plugin that wraps the logic for ease of use), it could be packaged as a library or toolkit for others
to use. One could imagine a world where **KotlinX SPI** is analogous to KotlinX Serialization – you add an annotation and a Gradle plugin,
and get multiplatform service loading. This would still be opt-in (as serialization is), but widely recommended for those who need it. It’s
plausible that JetBrains could bless such an approach if demand is high, especially given requests from frameworks like Ktor for a better
solution.

**Role of Gradle and Tooling:** We should not underestimate the role of build tools. Even if the compiler plugin becomes standard, a Gradle
plugin will likely be involved to configure it (similar to how `kotlinx-serialization` plugin is applied). It might also handle some edge
cases like including service metadata in packaged artifacts. Gradle can also aid in making the solution backward-compatible or
configurable (for example, turning off service discovery in certain builds if needed for size reasons). There’s also interest in build-time
enhancements from the community; some Gradle plugins exist to fill various KMP gaps, so an SPI Gradle plugin could gain traction if
introduced.

**Need for New Language Keywords?:** Introducing new keywords (like `service interface` or similar) seems **unlikely to be justified** in
Kotlin’s design philosophy, unless service discovery becomes a ubiquitous need. The preference would be to handle it via annotations and
compiler plugins, which are more lightweight from a language perspective. Kotlin has shown that it can deliver high-level features (like
serialization, Android extensions, etc.) through the plugin system without making them keywords. So, it’s reasonable to expect that service
loading can be standardized without new syntax. If anything, the language might add a standard annotation in a future version (so that it’s
part of the Kotlin standard library), but the heavy lifting would still be done by the compiler behind the scenes.

**Broad Adoption Considerations:** For any solution to be broadly adopted, it should be **simple to opt into, well-documented, and reliable
**. The library-based approach can be standardized de facto: for example, if SweetSPI (or a similar library) becomes stable and popular,
many library authors might start using `@Service` from that library, and many app developers might include SweetSPI’s runtime to load
services. Over time, this could become a convention. The advantage of a de facto standard is that it could later be replaced under the hood
by official support without breaking user code. For instance, if everyone uses `dev.whyoleg.sweetspi.Service` annotation and JetBrains later
introduces an identical `kotlin.spi.Service` annotation, the community could switch to the official one, or the library could adapt to use
the compiler plugin if available. The key is aligning on one approach.

JetBrains could also standardize this via a **KEEP (Kotlin Enhancement Proposal)**. Perhaps a KEEP might be written to discuss “Static
Service Loading for KMP.” That proposal could outline something like the kotlinx.spi approach or an official annotation. If accepted, it
might target a Kotlin 2.x release. Until then, the advice is to use the best available library solution and design it in a way that is
forward-compatible with potential official changes.

**Examples and Use Cases:** To ground this in examples: consider a **plugin system for a multiplatform app** where you have multiple feature
modules, each providing an implementation of an `AnalyticsProvider` interface. Using Approach 1 (annotations+KSP), each feature module
annotates its `AnalyticsProvider` implementation. At compile time, code is generated to register all providers. The app can then simply do
`AnalyticsProvider.allProviders()` (generated function) to get them. If tomorrow Kotlin provides a built-in SPI, those modules might instead
mark classes with a standard annotation, but the overall usage would remain similar. Another example is a **DI framework**: A multiplatform
DI library could use compile-time discovery to find all classes annotated with `@Inject` or all modules annotated with `@Module` and
automatically wire them together across platform boundaries. This could greatly simplify using DI in KMP (which currently often requires
manual wiring on Native because reflection isn’t available). The approaches we discussed could underpin such a framework.

**Binary Size and Performance Recap:** All feasible solutions operate with binary size and performance in mind. They avoid the reflection
and I/O of traditional Java SPI, which is especially beneficial on Android (where, for example, Ktor noted that `ServiceLoader.load` can be
slow ([WhatsNew 3.0 | Ktor Framework](https://ktor.io/changelog/3.0/#:~:text=WhatsNew%203.0%20,load%20calls%20%28like%20this%29))). Instead,
you pay a one-time cost at build. The added code (to register or list services) is usually very small. For instance, listing 10
implementations might produce on the order of 10 lines of instantiation code – negligible in size. On Kotlin/Native and Wasm, ensuring the
linker keeps those instances is crucial (and is handled by referencing them from a reachable place). The compile-time list inherently does
that by referencing each implementation, so they won’t be stripped. Thus, these approaches align with Kotlin’s philosophy of avoiding
unnecessary runtime costs by doing work at compile time when possible.

**Conclusion:** There are multiple viable paths to achieve compile-time service loading in Kotlin Multiplatform:

- *Library + Code Generation (KSP)* – **Available now**, relatively easy to implement, and works across platforms. This is the recommended
  approach for current projects in need of SPI. It’s poised to be widely adopted, especially if a common library emerges.
- *Compiler Plugin (IR)* – **Powerful but experimental**. In the future, this could become mainstream via an official plugin or KotlinX
  library. It offers a seamless experience and could be standardized for broad use, but needs more incubation.
- *Gradle-Orchestrated Registry* – **Pragmatic** approach that can complement either of the above. It can be used to glue things together
  without requiring every module to have a direct dependency on the SPI logic. This approach might be particularly useful in large projects
  or for retrofitting SPI into existing modules by handling it at the application assembly stage.
- *New Language Feature* – **Unlikely in the near term** given the capabilities of plugins, but remains an option. If introduced, it would
  make SPI a built-in feature of Kotlin, simplifying usage to the maximum extent. Until then, the language is flexible enough to allow
  library and compiler-plugin solutions to flourish.

All these solutions share the goal of making **modular and plugin-based design a first-class citizen in Kotlin Multiplatform**, with
compile-time safety and without sacrificing performance. The community momentum (as seen by libraries and prototypes) is towards a
standardized solution. It’s quite feasible that within a couple of years, Kotlin developers will have a clear, officially recommended way to
do service loading that works on every platform out-of-the-box. In the meantime, adopting one of the compile-time approaches outlined above
will ensure your multiplatform projects are future-proof and efficient, without waiting for runtime surprises.

**Sources:**

- Whyoleg’s SweetSPI – *“Simple SPI for Kotlin Multiplatform”*, showing KSP-based service discovery (24 targets support, automatic
  loading) ([GitHub - whyoleg/sweet-spi: Simple SPI (Service Provider Interface) for Kotlin Multiplatform (equivalent of JVM's Service Loader)](https://github.com/whyoleg/sweet-spi#:~:text=,setup%20of%20KSP%20and%20runtime)) ([GitHub - whyoleg/sweet-spi: Simple SPI (Service Provider Interface) for Kotlin Multiplatform (equivalent of JVM's Service Loader)](https://github.com/whyoleg/sweet-spi#:~:text=,which%20is%20available%20at%20runtime)).
- ZhangKe’s KRouter SPI announcement – discusses adding an annotation-based SPI in a KMP library (need for custom solution since KMP lacks
  it, usage of `@Service`
  annotation) ([KRouter Now Supports Kotlin Multiplatform SPI Mechanism | by ZhangKe | Medium](https://medium.com/@kezhang404/krouter-now-supports-kotlin-multiplatform-spi-mechanism-8d1348436430#:~:text=The%20above%20method%20leverages%20Java%E2%80%99s,to%20devise%20our%20own%20solution)) ([KRouter Now Supports Kotlin Multiplatform SPI Mechanism | by ZhangKe | Medium](https://medium.com/@kezhang404/krouter-now-supports-kotlin-multiplatform-spi-mechanism-8d1348436430#:~:text=%2F%2F%20Application%20fun%20main%28%29%20,HtmlParser%3E%28%29)).
- Kotlinx.SPI Prototype – proof-of-concept by Sebastian Sellmair illustrating a compiler plugin approach for static service registration (
  compile-time known services, custom plugin and
  runtime) ([kotlinx-spi/ReadMe.md at master · sellmair/kotlinx-spi · GitHub](https://github.com/sellmair/kotlinx-spi/blob/master/ReadMe.md#:~:text=This%20spi%20implementation%2C%20whilst%20also,be%20generated%20to%20link%20them)) ([kotlinx-spi/ReadMe.md at master · sellmair/kotlinx-spi · GitHub](https://github.com/sellmair/kotlinx-spi/blob/master/ReadMe.md#:~:text=Kotlin%20Native%20Implementation)).
- StackOverflow (Dmitri’s answer) – notes how Ktor uses `ServiceLoader` on JVM but had to default differently on Native/JS (highlighting the
  gap in multiplatform service
  loading) ([How do I configure the engine for KTOR HTTPclient Multiplatform Kotlin projects? Addition of SSL certificate is also required - Stack Overflow](https://stackoverflow.com/questions/59229189/how-do-i-configure-the-engine-for-ktor-httpclient-multiplatform-kotlin-projects#:~:text=Not%20possible%20to%20add%20Apache%2C,Also)) ([How do I configure the engine for KTOR HTTPclient Multiplatform Kotlin projects? Addition of SSL certificate is also required - Stack Overflow](https://stackoverflow.com/questions/59229189/how-do-i-configure-the-engine-for-ktor-httpclient-multiplatform-kotlin-projects#:~:text=Image%3A%20List%20of%20engines%20by,platform)).
- Ktor issue (KTOR-7698) – pointed out performance issues with Java’s ServiceLoader on
  Android ([WhatsNew 3.0 | Ktor Framework](https://ktor.io/changelog/3.0/#:~:text=WhatsNew%203.0%20,load%20calls%20%28like%20this%29)),
  which compile-time solutions would mitigate.
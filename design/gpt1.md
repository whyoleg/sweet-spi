# Kotlin Multiplatform and Dynamic Service Discovery: A Comparative Analysis

Great. I’ll explore whether Kotlin Multiplatform needs a feature similar to Java’s `ServiceLoader`, looking into real-world use cases, both
runtime and compile-time approaches, and how similar needs are addressed in other languages and libraries.

I’ll compare across multiple platforms (JVM, Native, JS, Wasm) and consider both Kotlin Multiplatform and JVM-only Kotlin. I’ll also dive
into approaches in other ecosystems (like Swift, Rust, .NET) to see whether and how they solve similar problems, or why such a mechanism
might be unnecessary.

I’ll get back to you with a breakdown of use cases, solutions, and design trade-offs across ecosystems.

# Kotlin Multiplatform and Dynamic Service Discovery: A Comparative Analysis

## Introduction

Kotlin Multiplatform (KMP) enables sharing code across JVM, Native (iOS, macOS, Linux, etc.), JavaScript, and even WebAssembly targets. One
feature Java developers often take for granted is **dynamic service discovery** – for example, Java’s `ServiceLoader` mechanism which
automatically finds implementations of an interface on the classpath at
runtime ([How to use Java ServiceLoader to create extensible applications | Literate Java](https://literatejava.com/extensibility/java-serviceloader-extensible-applications/#:~:text=Your%20application%20can%20then%20find,load%20service%20implementations%20like%20this)) ([How to use Java ServiceLoader to create extensible applications | Literate Java](https://literatejava.com/extensibility/java-serviceloader-extensible-applications/#:~:text=The%20ServiceLoader%20mechanism%20enables%20your,the%20specific%20plugins%20you%20ship)).
This report investigates whether KMP needs a similar capability. We’ll explore real-world use cases for dynamic discovery (like plugin
systems, modular architectures, and dependency injection), and how such patterns are handled on different KMP targets. We also compare how
other languages and platforms address (or avoid) this need, including Java, Swift, Rust, .NET, JavaScript/TypeScript, and Go. Finally, we
discuss the trade-offs between runtime and compile-time approaches, limitations, and scenarios where a ServiceLoader-like mechanism may be
unnecessary or even undesirable.

## Use Cases Requiring Dynamic Discovery

Dynamic discovery of implementations is useful whenever we want a **flexible, extensible architecture** where new modules can be added
without modifying the core application. Key use cases include:

- **Plugin Systems:** Applications like IDEs or game engines allow third-party plugins to extend functionality. For example, an application
  might load all JARs in a “plugins” folder and automatically discover classes implementing a `Plugin` interface. Java’s Service Provider
  Interface (SPI) is designed for this kind of
  extensibility ([Implementing Plugins with Java's Service Provider Interface](https://reflectoring.io/service-provider-interface/#:~:text=The%20Service%20Provider%20Interface%20was,to%20make%20applications%20more%20extensible)) ([How to use Java ServiceLoader to create extensible applications | Literate Java](https://literatejava.com/extensibility/java-serviceloader-extensible-applications/#:~:text=The%20ServiceLoader%20mechanism%20enables%20your,the%20specific%20plugins%20you%20ship)).
  In Swift, developers have expressed interest in a plugin API to load user-defined modules at runtime (e.g. Swift Format wanting custom
  rule
  plugins) ([Swift dynamic loading API - Discussion - Swift Forums](https://forums.swift.org/t/swift-dynamic-loading-api/39495#:~:text=allevato%20,18%2C%202020%2C%209%3A10pm%20%203)).
  In Rust, libraries like `dynamic_plugin` and `vanguard-plugin` exist to facilitate loading compiled “.so” plugin modules for
  extensibility ([dynamic_plugin - Rust](https://docs.rs/dynamic-plugin#:~:text=In%20many%20pieces%20of%20software%2C,like%20systems%20are%20often%20used)).

- **Modular or Layered Frameworks:** Large frameworks often allow swapping or adding components. For instance, **Ktor (Kotlin)** supports
  multiple HTTP client engines (CIO, Apache, OkHttp, etc.) as separate modules. On the JVM, Ktor can use a ServiceLoader-like lookup to pick
  a default engine if one isn’t
  specified ([ktor/ktor-client/ktor-client-core/common/src/io/ktor/client/HttpClient.kt at main · ktorio/ktor · GitHub](https://github.com/ktorio/ktor/blob/main/ktor-client/ktor-client-core/common/src/io/ktor/client/HttpClient.kt#:~:text=%2A%20,Default%20Engine)).
  In a multiplatform context, however, Ktor had to handle each platform differently (as we’ll see below). Another example is **JDBC in Java
  **, where JDBC drivers are discovered by the presence of a file in `META-INF/services/java.sql.Driver` in each driver
  JAR ([META-INF/services/java.sql.Driver file - Stack Overflow](https://stackoverflow.com/questions/17598152/meta-inf-services-java-sql-driver-file#:~:text=JDBC%204,Driver)) –
  this is effectively a plugin mechanism for database drivers.

- **Dependency Injection (DI) and Inversion of Control:** DI frameworks often need to discover or register providers/bindings. Some use
  runtime reflection or classpath scanning (e.g. Spring Framework scanning for components at startup). Others use explicit or compile-time
  techniques (like Dagger’s annotation processing in Java). In Kotlin Multiplatform, libraries like **Koin** avoid reflection entirely by
  using a DSL to declare modules (i.e. you manually register implementations in
  code) ([What DI framework do you use in Kotlin? - Reddit](https://www.reddit.com/r/Kotlin/comments/16jbdiu/what_di_framework_do_you_use_in_kotlin/#:~:text=What%20DI%20framework%20do%20you,probably%20used%20most%20after)).
  This explicit approach works across platforms, since it doesn’t rely on any dynamic class loading – everything is wired via code. Yet, one
  might wonder if a generic discovery mechanism could simplify certain DI use cases in KMP, such as automatically registering
  platform-specific implementations.

In all these scenarios, the core theme is **decoupling** – the core system doesn’t need to know about concrete implementations at compile
time. Instead, implementations can be added or removed independently, and the system finds what’s available at runtime. Next, we examine how
Kotlin Multiplatform handles this (spoiler: there is no built-in `ServiceLoader` in common code), and how each platform target and other
ecosystems approach the problem.

## Dynamic Implementation Discovery in Kotlin Multiplatform

Kotlin Multiplatform does **not currently provide a built-in equivalent** to Java’s `ServiceLoader` in common code. The ability to list
classes or load one by name is heavily platform-dependent:

- **JVM:** The Kotlin/JVM target can, of course, leverage the Java reflection APIs and ServiceLoader. On the JVM, one could use
  `ServiceLoader.load(MyInterface::class.java)` to find implementations on the
  classpath ([How to use Java ServiceLoader to create extensible applications | Literate Java](https://literatejava.com/extensibility/java-serviceloader-extensible-applications/#:~:text=Your%20application%20can%20then%20find,load%20service%20implementations%20like%20this)),
  or use other reflection-based libraries. Kotlin’s interoperability means you can use these mechanisms when running on JVM. However, this
  only covers the JVM part of a multiplatform project.
- **Native (Kotlin/Native for iOS, macOS, etc.):** Kotlin/Native compiles down to machine code and does **not support dynamic class loading
  or full reflection**. There is no `Class.forName` or runtime scanning of classes – in fact, Kotlin/Native only supports very limited
  reflection (like getting class references or simple type introspection) and is “unlikely to support full
  introspection” ([Reflection? - Native - Kotlin Discussions](https://discuss.kotlinlang.org/t/reflection/4054#:~:text=Kotlin%2FNative%20will%20support%20a%20very,cases%20for%20reflection%2C%20please)).
  As one Kotlin developer put it, *“the key feature that is missing (in K/N) is `java.lang.Class.forName(String)`,”* which prevents using
  patterns that load implementations at
  runtime ([Reflection? - Native - Kotlin Discussions](https://discuss.kotlinlang.org/t/reflection/4054#:~:text=The%20key%20feature%20that%20is,forName%28String)).
  The practical effect is that on Native, all implementations must be known at compile time and wired explicitly or via generated code.
  Attempts to simulate dynamic loading (e.g. using `dlopen` on a dynamic library) are non-trivial and not officially supported for
  high-level Kotlin
  classes ([Kotlin Native plugin loading mechanism - Native - Kotlin Discussions](https://discuss.kotlinlang.org/t/kotlin-native-plugin-loading-mechanism/15645#:~:text=Is%20there%20a%20way%20of,platform%20plugins%20in%20Kotlin%20Common)) ([Kotlin Native plugin loading mechanism - Native - Kotlin Discussions](https://discuss.kotlinlang.org/t/kotlin-native-plugin-loading-mechanism/15645#:~:text=Thanks%20for%20your%20reply%20and,and%20not%20as%20Kotlin%20classes)).
- **JavaScript:** Kotlin/JS outputs to JS, which does support dynamic behavior (you can `import()` modules dynamically in an ES module
  environment, or use global scripts). However, Kotlin/JS doesn’t have a reflection library analogous to Java’s. You can’t list all
  subclasses of an interface in pure Kotlin/JS code without some manual help. Still, since the runtime is JavaScript, one could conceive a
  manual plugin registry or use dynamic imports. For example, JavaScript’s **dynamic `import()`** is *“a function-like expression that
  allows loading an ECMAScript module asynchronously and
  dynamically”* ([import() - JavaScript - MDN Web Docs - Mozilla](https://developer.mozilla.org/en-US/docs/Web/JavaScript/Reference/Operators/import#:~:text=The%20import,ECMAScript%20module%20asynchronously%20and%20dynamically)).
  This could be used to load plugin modules at runtime if the environment (browser or Node) and bundler configuration allows. In practice,
  many Kotlin/JS apps bundle everything ahead of time, so “discovery” might just mean having a list of modules to import. The dynamic nature
  of JS can mitigate the need for a formal ServiceLoader – you can simply iterate over an array of module paths and import them. Type safety
  for such plugins would be weaker (since it’s all `Any` until cast to expected plugin interface).
- **Wasm:** Kotlin/Wasm is an experimental target that compiles to WebAssembly. Like Native, WebAssembly is compiled ahead-of-time. There is
  no built-in mechanism to load new Wasm code at runtime (apart from instantiating separate modules via host APIs, which would require the
  host (JS) to orchestrate). So, Kotlin/Wasm should be treated similarly to Native: dynamic service loading is not available at runtime. Any
  extensibility must be planned at compile-time (or handled by the JavaScript host environment, if applicable).

**Current KMP Workarounds:** Because of these differences, multiplatform libraries that need pluggable components typically adopt one of two
strategies on KMP:

1. **Manual registration:** Each implementation module explicitly registers itself with the core at initialization. For example, the *
   *Kotlinx Multik** library (for multi-dimensional arrays) supports multiple “engine” implementations. Multik doesn’t scan for engines;
   instead it provides an API to add an engine. Each engine module calls `Multik.addEngine(myEngine)` in its init code, or the application
   does so
   explicitly ([Multik](https://kotlin.github.io/multik/multik-core/org.jetbrains.kotlinx.multik.api/-multik/index.html#:~:text=The%20basic%20object%20through%20which,implementation%20that%20requires%20the%20engine)) ([Multik](https://kotlin.github.io/multik/multik-core/org.jetbrains.kotlinx.multik.api/-multik/index.html#:~:text=fun%20addEngine%20)).
   The Multik API then allows selecting the current engine by name. If no engine was registered, calling engine-dependent functions throws
   an
   error ([Multik](https://kotlin.github.io/multik/multik-core/org.jetbrains.kotlinx.multik.api/-multik/index.html#:~:text=ndarray%20creation%20and%20interfaces%20Math,implementation%20that%20requires%20the%20engine)).
   This design avoids any reflection and works on all targets, at the cost of requiring a manual step to register engines.
2. **Expect/Actual or build-time selection:** Some multiplatform libraries use Kotlin’s expect/actual mechanism to choose an implementation
   per platform at compile time. In this pattern, you might have an `expect object PluginRegistry` in common code, and then actual
   implementations on each target that return the appropriate plugins. The downside is that you must edit each platform module when adding
   new plugins (so it’s not “dynamic” in the true sense, but it centralizes the selection logic per platform).

**The Ktor Example (Engines):** Ktor’s HTTP client is a real-world illustration. Ktor supports multiple HTTP client engines, but if you
simply do `HttpClient()` without specifying one, what happens? On different platforms Ktor handles it differently:

- On **JVM**, Ktor does use a ServiceLoader-like mechanism to find an engine. The default engine is resolved by scanning available engine
  implementations on the classpath and picking the first
  alphabetically ([Engines - 客户端 - Ktor](https://ktor.kotlincn.net/clients/http-client/engines.html#:~:text=val%20client%20%3D%20HttpClient)). (
  In fact, Ktor’s documentation notes that on JVM this uses a service loader under the
  hood ([ktor/ktor-client/ktor-client-core/common/src/io/ktor/client/HttpClient.kt at main · ktorio/ktor · GitHub](https://github.com/ktorio/ktor/blob/main/ktor-client/ktor-client-core/common/src/io/ktor/client/HttpClient.kt#:~:text=%2A%20,Default%20Engine)).)
  This adds some startup overhead on Android and the docs recommend specifying an engine explicitly for performance-critical
  cases ([ktor/ktor-client/ktor-client-core/common/src/io/ktor/client/HttpClient.kt at main · ktorio/ktor · GitHub](https://github.com/ktorio/ktor/blob/main/ktor-client/ktor-client-core/common/src/io/ktor/client/HttpClient.kt#:~:text=%2A%20,Default%20Engine)) ([ktor/ktor-client/ktor-client-core/common/src/io/ktor/client/HttpClient.kt at main · ktorio/ktor · GitHub](https://github.com/ktorio/ktor/blob/main/ktor-client/ktor-client-core/common/src/io/ktor/client/HttpClient.kt#:~:text=,where%20initialization%20speed%20is%20critical)).
- On **Native**, there is no such reflection. Instead, Ktor’s docs state *“for Native, the engine [is] detected during static
  linkage”* ([Engines - 客户端 - Ktor](https://ktor.kotlincn.net/clients/http-client/engines.html#:~:text=In%20the%20case%20of%20the,the%20artifacts%20you%20have%20included)).
  In practice, this means you must include exactly one native engine in your program (e.g. the curl-based or CIO native engine), and at link
  time the Kotlin/Native compiler will ensure the calls resolve to that engine’s implementation. If you include multiple native engines,
  you’d likely have to choose one manually (or you’d get duplicate symbol issues). So Ktor sidesteps dynamic discovery on Native by making
  the developer include the desired engine.
- On **JS**, Ktor uses a predefined engine (the only choice is typically the browser’s fetch or Node’s http
  module) ([Engines - 客户端 - Ktor](https://ktor.kotlincn.net/clients/http-client/engines.html#:~:text=the%20artifacts%20you%20have%20included)),
  so no discovery is needed – it’s hardcoded.

This hybrid approach allows Ktor to be multiplatform despite the lack of a uniform ServiceLoader. The **open issue KT-53056** in Kotlin’s
issue tracker (titled “Multiplatform equivalent of JVM’s ServiceLoader”) suggests that library authors (including the Ktor team) have felt
this pain and have asked for a first-class solution. As of early 2025, there isn’t an official multiplatform ServiceLoader yet, so library
authors either restrict dynamic loading to JVM or use compile-time techniques.

## How Other Languages and Platforms Handle Plugin Discovery

To better understand the need and possible solutions, let’s compare how several languages/platforms implement service discovery or plugin
architectures:

### Java (and Kotlin on the JVM)

The Java ecosystem has a mature solution for runtime discovery: the **Service Provider Interface (SPI)** with `ServiceLoader`. A *service*
in this context is an interface (or abstract class), and *service providers* are implementations usually packaged in separate
JARs ([Implementing Plugins with Java's Service Provider Interface](https://reflectoring.io/service-provider-interface/#:~:text=,a%20cache%20of%20services%20already)).
Java’s mechanism works by providers declaring themselves in a file under `META-INF/services/` in their JAR. The file is named after the
service (interface) and contains the concrete implementation class
name ([Implementing Plugins with Java's Service Provider Interface](https://reflectoring.io/service-provider-interface/#:~:text=Apart%20from%20the%20service%20providers,INF%2Fservices)) ([How to use Java ServiceLoader to create extensible applications | Literate Java](https://literatejava.com/extensibility/java-serviceloader-extensible-applications/#:~:text=%2A%20you%20build%20a%20%60META,discover%20%26%20load%20registered%20services)).
At runtime, `ServiceLoader.load(MyService.class)` will scan the classpath for all such files and instantiate the listed classes, giving the
application a list of implementations to
use ([How to use Java ServiceLoader to create extensible applications | Literate Java](https://literatejava.com/extensibility/java-serviceloader-extensible-applications/#:~:text=Your%20application%20can%20then%20find,load%20service%20implementations%20like%20this)).

This approach is used in many places:

- **JDK Examples:** JDBC drivers in modern Java rely on ServiceLoader. As of JDBC 4.0, you’re no longer required to manually call
  `Class.forName` on the driver; if the driver JAR has `META-INF/services/java.sql.Driver` with the driver class name, it gets loaded
  automatically ([META-INF/services/java.sql.Driver file - Stack Overflow](https://stackoverflow.com/questions/17598152/meta-inf-services-java-sql-driver-file#:~:text=JDBC%204,Driver)) ([revisit how JDBC drivers are loaded · Issue #5 · julianhyde/sqlline](https://github.com/julianhyde/sqlline/issues/5#:~:text=julianhyde%2Fsqlline%20github,have%20this%20file%20these%20days)).
  Another example is the Java logging framework and other pluggable APIs in JDK which use SPI to load implementations.
- **Libraries:** Many libraries use ServiceLoader for extensibility. For instance, the Java JSON processing API (JSR 374) uses ServiceLoader
  to find providers, and frameworks like **TestNG** added support for ServiceLoader to load extensions (like custom test listeners) by just
  dropping a file in
  `META-INF/services` ([New TestNG feature: support for ServiceLoader - Google Groups](https://groups.google.com/g/testng-users/c/ZVloM26gEoI#:~:text=New%20TestNG%20feature%3A%20support%20for,you%20create%20a%20file)).
  Even Kotlin’s own JVM libraries can leverage it (e.g., kotlinx.serialization on JVM uses ServiceLoader to find built-in serializers, if I
  recall correctly).
- **Reflection & Classpath Scanning:** Beyond ServiceLoader, Java developers often use reflection to discover classes. Frameworks like
  Spring or CDI (Contexts and Dependency Injection) perform **classpath scanning** – the framework will iterate over all classes in the
  classpath (using `ClassLoader` APIs or libraries like Reflections) to find those annotated with certain annotations (e.g., `@Component`).
  This is more free-form than ServiceLoader and allows filtering by annotation or interface at runtime. The trade-off is that it can be
  slow (especially on Android, which is why Android favors compile-time injection now) and it needs all classes to be loadable. Tools like
  Spring mitigate performance issues with indexing or by letting you limit the packages to scan.

**Summary (Java):** Java’s runtime discovery is powerful and flexible on the JVM. Kotlin on JVM can fully utilize these (Kotlin classes are
Java classes at runtime). However, this relies on a dynamic classloading environment, which KMP’s other targets lack.

### Swift (Apple Platforms)

Swift does not have a built-in `ServiceLoader` equivalent. On Apple platforms (iOS, macOS), the typical way to have plugins is either
through the Objective-C runtime or dynamic libraries:

- **Dynamic Libraries/Bundles:** macOS apps can be extensible via loading dynamic frameworks or bundles at runtime (using `Bundle.load()` or
  `dlopen` for `.dylib`). In pure Swift, this is cumbersome because Swift doesn’t expose a reflection API to enumerate types or protocols
  easily. If the classes are Objective-C compatible (marked with `@objc`), one could potentially use the Objective-C runtime to find classes
  by name or protocol conformance. But pure Swift types are not visible to Obj-C runtime unless bridged.
- **No Standard Plugin API:** There has been discussion in the Swift community about introducing a plugin system. A pitched example imagines
  a `Plugin.load(path)` API that could load a module and then query for all types conforming to a given
  protocol ([Swift dynamic loading API - Discussion - Swift Forums](https://forums.swift.org/t/swift-dynamic-loading-api/39495#:~:text=import%20Plugins%20%2F%2F%20core%20library%2C,import%20MyPluginAPI)).
  This would require Swift to generate metadata that can be queried (similar to how SwiftUI can find types with certain attributes via
  reflection, which is limited). The Swift forums show interest in such features, especially for server-side Swift (e.g., allowing
  third-party rules in swift-format, as noted by Tony
  Allevato ([Swift dynamic loading API - Discussion - Swift Forums](https://forums.swift.org/t/swift-dynamic-loading-api/39495#:~:text=allevato%20,18%2C%202020%2C%209%3A10pm%20%203))).
  As of now, Swift doesn’t have this – any “dynamic discovery” must be implemented manually.
- **Manual Registration:** In practice, Swift libraries sometimes fall back to patterns similar to manual registration. For instance, a
  Swift package might have a registry singleton where each plugin calls a registration function when initialized. Since Swift code runs
  static initializers at program start (like `let _ = MyPluginRegistrar = registerPlugin()`), including a plugin module can trigger code to
  register it. But you still have to link that plugin in at build time or load it via `dlopen`.

**Apple’s Stance:** Especially on iOS, dynamic loading of code is restricted (for security, App Store rules prevent executing downloaded
binary code). So plugin architectures on iOS are usually compile-time modular (feature flags, static inclusion) rather than true runtime
extensibility. On macOS or server-side Swift (Linux), it’s more feasible (e.g., Swift can use `dlopen` on Linux to load `.so` plugins). The
**Swift-Plugin-Manager** project on GitHub is an example aiming to simplify dynamic plugin loading for server-side Swift
apps ([GitHub - hassila/swift-plugin-manager: Dynamic loading of Swift plug-ins for extensible architectures](https://github.com/hassila/swift-plugin-manager#:~:text=Support%20for%20dynamic%20loading%20and,to%20extend%20hosting%20application%20functionality)) ([GitHub - hassila/swift-plugin-manager: Dynamic loading of Swift plug-ins for extensible architectures](https://github.com/hassila/swift-plugin-manager#:~:text=Sample%20usage)),
effectively building a custom ServiceLoader-like utility using `dlopen` under the hood.

**Summary (Swift):** No built-in dynamic discovery; can load dynamic libs manually on some platforms. Swift encourages designs where you
pass in dependencies or use protocols for flexibility, but actual wiring is usually done in code or via SwiftPM at compile time rather than
runtime scanning.

### Rust

Rust intentionally does not have a runtime reflection or dynamic class loading in the typical sense – it’s a systems language favoring
static linking and compile-time guarantees. However, Rust can achieve plugin mechanisms in a few ways:

- **Trait Objects and Manual Loading:** A plugin system in Rust often uses a common trait (interface). The plugins are separate dynamic
  library (.dll/.so) crates that implement that trait. At runtime, the host program can load the `.so` using the `libloading` crate (which
  wraps `dlopen` and
  similar) ([how can I add dynamic loading to do "plugins" for my Rust app?](https://www.reddit.com/r/rust/comments/144zmwk/how_can_i_add_dynamic_loading_to_do_plugins_for/#:~:text=how%20can%20I%20add%20dynamic,a%20look%20at%20eg%20libloading)).
  Since Rust cannot natively instantiate a type from a dynamic lib by name (no reflection), the common approach is to expose a C ABI
  function from the plugin like `extern "C" fn register(plugin_registry: &mut Registry)` or `create_plugin() -> Box<dyn PluginTrait>`. The
  host uses `libloading` to get that function pointer and calls it to obtain the plugin instance. This is analogous to how C plugins work.
  Crates like **dynamic_plugin** and **vanguard-plugin** provide some scaffolding to make this safer (ensuring version compatibility of
  interfaces,
  etc.) ([dynamic_plugin - Rust](https://docs.rs/dynamic-plugin#:~:text=In%20many%20pieces%20of%20software%2C,like%20systems%20are%20often%20used)).
- **No Hot Reload or Unload:** These dynamically loaded plugins in Rust come with caveats – they must be compiled with the exact same Rust
  compiler and dependencies as the host (to ensure the trait’s layout and other ABI details match). This is similar to Go’s situation (
  explained below). Rust doesn’t guarantee a stable ABI for Rust types across binaries, so one often uses a C ABI for the plugin boundary.
  Because of these challenges, dynamic plugins in Rust are less common, and there is no stable support in Cargo for building plugins
  separately (each plugin is basically a separate binary artifact).
- **Compile-Time Plugins:** Many Rust projects prefer **compile-time extensibility**. For example, the Rocket web framework uses code
  generation (procedural macros) to collect route handlers at compile time, rather than runtime scanning for them. If a Rust application
  wants extensibility, a common pattern is to require users to list the plugins in a config file or at build time, and then use a build
  script or macro to include them. In fact, the authors of `dynamic_plugin` note that static linking by generating code to include plugins
  may be simpler in many
  cases ([plugin package - plugin - Go Packages](https://pkg.go.dev/plugin#:~:text=,certain%20flags%20and%20environment%20variables)) ([plugin package - plugin - Go Packages](https://pkg.go.dev/plugin#:~:text=,executable%20in%20the%20usual%20way)).
  This gives up true runtime addition of new code, but maintains Rust’s safety and simplifies distribution (one binary).

**Summary (Rust):** No built-in reflection. Dynamic plugins possible via `dlopen` and trait objects, but with significant limitations. Many
Rustaceans resort to compile-time approaches or even external processes (e.g., run plugins as separate processes and communicate via IPC)
for extensibility. Rust’s philosophy leans toward upfront knowledge of types (monomorphization and zero-cost abstractions), so a
ServiceLoader mechanism is not in its idiom.

### .NET (C# and others)

The .NET platform historically supports reflection and dynamic loading of assemblies:

- **Reflection and Assembly Scanning:** In .NET, you can load an assembly at runtime (`Assembly.LoadFile` or `LoadFrom`) and then use
  `assembly.GetTypes()` to enumerate classes, checking which implement a certain interface or have a certain attribute. This is frequently
  used in plugin scenarios – e.g., a hosting app might load all DLLs in a “Plugins” folder and find classes that implement an `IPlugin`
  interface, then instantiate them via `Activator.CreateInstance`. This is analogous to Java’s reflection approach.
- **Managed Extensibility Framework (MEF):** .NET 4.0 introduced MEF as a built-in library for composing applications from loosely coupled
  parts. MEF uses attributes like `[Export(interfaceType)]` on plugin classes and `[Import]` on fields or properties where the host wants an
  implementation. At runtime, MEF will scan available assemblies (or ones you explicitly add to a `CompositionContainer`) to find exports
  and wire them to imports. Essentially, MEF is doing a reflection-based discovery under the hood, but it provides a structured API.
  Microsoft’s documentation describes MEF as a way to *“discover and load libraries at runtime without hard-coded
  references”* ([Dynamic Plugin Loading Using MEF - Foci Solutions](https://www.focisolutions.com/2017/11/dynamic-plugin-loading-using-mef/#:~:text=The%20Managed%20Extensibility%20Framework%20,and%20inversion%20of%20control%20patterns)) –
  which is exactly the ServiceLoader concept. MEF is often used in apps needing a plugin architecture (for example, the Visual Studio IDE
  uses MEF for its extensions).
- **Source Generators (Compile-time):** In modern .NET (Core 5+), source generators can be used to avoid some runtime reflection. For
  instance, instead of using reflection to find all implementations, a source generator can run at build time and produce a registry class
  that has a compile-time list of available implementations. This is helpful in AOT compilation scenarios (like Blazor WebAssembly or .NET
  Native) where reflection might be limited. An example is the **Community Toolkit DI** which uses source generators to auto-register view
  models and services, rather than relying on scanning. Similarly, some libraries generate code for serialization or routing to avoid
  runtime costs. However, these require that all parts (plugins) are known at build time (or at least that their assemblies are referenced
  so the generator sees them).
- **.NET Core and Plugin Isolation:** .NET Core introduced `AssemblyLoadContext` to load plugins in isolation (and even unload them).
  Projects like **Microsoft Extensions Hosting** allow loading assemblies from files and adding them to an `IServiceCollection` via
  reflection. There are also community solutions like **Prism Modularity** or **AddApplicationParts** in ASP.NET for modular loading. These
  remain reflection-based under the hood.

**Summary (.NET):** .NET offers robust reflection and a dedicated framework (MEF) for runtime discovery. The trade-off is similar to Java’s:
reflection can be slow or problematic with ahead-of-time compilation. Source generators are emerging as a way to shift work to compile time
for performance and AOT safety, indicating a trend toward hybrid approaches (dynamic for flexibility when needed, static for performance
where possible).

### JavaScript/TypeScript

JavaScript, being dynamically typed and interpreted, historically handles extensibility very easily:

- **Dynamic Module Loading:** In Node.js or modern JS, you can dynamically load modules. In Node (CommonJS), you might do
  `require(pluginPath)` where `pluginPath` is determined at runtime (say by reading a directory of plugins). This will execute that module
  and you can retrieve whatever it exports. In ES Modules, as mentioned, you can use `import(path)` which returns a Promise of the module
  object. There is no need for a ServiceLoader because JavaScript can treat module names as data. For example, a web app could have a
  configuration that lists plugin URLs and simply import them when needed.
- **No Interface Enforcement:** One challenge in JS/TS is that without a runtime type system, you can load anything – it’s up to the plugin
  author to follow a convention (e.g., exporting a function named `register(pluginHost)`). TypeScript can provide compile-time checking if
  the host project *explicitly* imports the plugin’s types or if you define an interface that plugin objects should conform to. But at
  runtime, there’s no automatic discovery by type, since types are erased.
- **Convention-based Discovery:** Many JS frameworks use conventions. For instance, if you want all files in a certain folder to be treated
  as plugins, you can require each of them. Tools like Webpack even have a feature (require.context) to load all files matching a pattern.
  In front-end apps, true runtime addition of new code is less common (usually all code is bundled), but for something like an **IDE (
  VSCode)** or **Electron app**, you can indeed load extension packages at runtime. VSCode, for example, loads extensions (which are just
  Node modules) by reading their manifest and using Node’s module loading.
- **Avoiding Need for Loader:** Often, plugin systems in JS are implemented by the plugin calling a known registration function. For
  example, a plugin might execute `PluginHost.register(new MyPlugin())` when loaded. The host simply loads the script and expects the plugin
  to register itself. This pattern doesn’t need scanning; it relies on executing code with side effects (similar to Go’s init approach or
  manual registration in other languages).

**Summary (JS/TS):** JavaScript’s dynamic nature makes a ServiceLoader unnecessary – you can load modules or scripts by name easily. The
ecosystem relies on conventions and explicit calls to hook things up. The downside is lack of compile-time safety and, in browser context,
ensuring the plugin code is available (which might involve user including a script or using a dynamic import).

### Go

Go’s approach is interesting because it largely forgoes runtime reflection for code loading, and instead leverages its initialization order
and tooling:

- **Compile-Time Registration via `init()`:** In Go, every package can have an `init()` function that runs when the program starts (if that
  package is imported). This leads to a common pattern for plugins: the plugin package’s `init()` registers the plugin in some central
  registry. For example, the Go standard library’s `database/sql` package uses this approach. Database driver packages (like
  `github.com/lib/pq` for Postgres) have an `init()` that calls `sql.Register("postgres", driver)`. The user simply does a blank import:
  `import _ "github.com/lib/pq"` in their main
  program ([Design patterns in Go's database/sql package - Eli Bendersky's website](https://eli.thegreenplace.net/2019/design-patterns-in-gos-databasesql-package/#:~:text=The%20trick%20is%20in%20the,blank%20import)).
  This import doesn’t directly use anything from the package, but it triggers the init, thereby registering the driver. Later, the user
  calls `sql.Open("postgres", connStr)` and `database/sql` knows about the driver because it was registered. This is essentially a *
  *compile-time plugin** mechanism – you decide which drivers to include at build time, and they self-register. Many extensible Go
  libraries (image decoders, CLI subcommands frameworks, etc.) use this pattern. It’s simple and avoids any reflection or scanning (just
  needs the developer to import the plugins).
- **Go Plugin Package (runtime)**: Go does have a `plugin` package in the standard library to load compiled plugins at runtime. You build a
  plugin by compiling a Go module with `go build -buildmode=plugin`, producing a `.so` file. The host program can then do
  `plugin.Open("pluginfile.so")` and use `Lookup` to get a symbol (function or variable) from
  it ([plugin package - plugin - Go Packages](https://pkg.go.dev/plugin#:~:text=A%20plugin%20is%20a%20Go,that%20has%20been%20built%20with)) ([plugin package - plugin - Go Packages](https://pkg.go.dev/plugin#:~:text=The%20ability%20to%20dynamically%20load,performance%20integration%20of%20separate%20parts)).
  This is analogous to `dlopen` and `dlsym` in C. However, the Go team warns that this plugin mechanism has **significant drawbacks**.
  Notably, plugins only work on certain OS (Linux, macOS, not Windows as of now) and require exact version matching of Go and
  dependencies ([plugin package - plugin - Go Packages](https://pkg.go.dev/plugin#:~:text=,applications%20intended%20to%20be%20portable)) ([plugin package - plugin - Go Packages](https://pkg.go.dev/plugin#:~:text=,certain%20flags%20and%20environment%20variables)).
  They cannot be unloaded once loaded, and any slight mismatch in build can cause runtime crashes. In practice, Go plugins are rarely used
  in mainstream projects due to these issues and the complexity of deployment (mixing a Go version, etc.). The Go documentation even states
  that often it’s simpler to just statically compile in the plugins via blank
  imports ([plugin package - plugin - Go Packages](https://pkg.go.dev/plugin#:~:text=,certain%20flags%20and%20environment%20variables)) ([plugin package - plugin - Go Packages](https://pkg.go.dev/plugin#:~:text=,executable%20in%20the%20usual%20way)).
- **No Reflection for Types:** Go has reflection for data (to inspect structs at runtime), but it **does not** allow enumerating packages or
  types at runtime. You cannot ask Go “give me all types implementing interface X” at runtime. So a ServiceLoader concept doesn’t exist. You
  either rely on build-time knowledge (the blank import trick) or maintain your own registry maps.

**Summary (Go):** Go leans heavily toward compile-time inclusion of plugins. The `init()` + blank import pattern is a distinctive solution
that avoids needing a ServiceLoader entirely – if you include the plugin, it’s registered; if you don’t, it’s not part of the binary. True
dynamic loading is possible but limited and often discouraged by Go’s designers due to complexity and safety
concerns ([plugin package - plugin - Go Packages](https://pkg.go.dev/plugin#:~:text=,load%20dangerous%20or%20untrusted%20libraries)) ([plugin package - plugin - Go Packages](https://pkg.go.dev/plugin#:~:text=,exactly%20the%20same%20source%20code)).

### Comparison Table of Approaches

To summarize the above across languages and platforms, the table below highlights whether dynamic runtime discovery is supported and what
typical mechanisms are used:

| **Platform**          | **Built-in Dynamic Discovery?**                                                                                                                                                                                              | **Common Approaches**                                                                                                                                                                                                                                                                                                                                                                                                                                                                         | **Notes/Examples**                                                                                                                                                                                                                                                                                                                                                                                                                    |
|-----------------------|------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|-----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| **Kotlin/JVM (Java)** | Yes – via Java reflection & ServiceLoader ([How to use Java ServiceLoader to create extensible applications                                                                                                                  | Literate Java](https://literatejava.com/extensibility/java-serviceloader-extensible-applications/#:~:text=Your%20application%20can%20then%20find,load%20service%20implementations%20like%20this))                                                                                                                                                                                                                                                                                             | *Runtime:* `ServiceLoader`, classpath scanning, reflection. <br>*Compile-time:* Kotlin Symbol Processing (KSP) or annotation processors to generate registries.                                                                                                                                                                                                                                                                       | ServiceLoader SPI (e.g. JDBC drivers) ([revisit how JDBC drivers are loaded · Issue #5 · julianhyde/sqlline](https://github.com/julianhyde/sqlline/issues/5#:~:text=julianhyde%2Fsqlline%20github,have%20this%20file%20these%20days)); Spring component scanning for DI. |
| **Kotlin/Native**     | No (no reflection or class loading) ([Reflection? - Native - Kotlin Discussions](https://discuss.kotlinlang.org/t/reflection/4054#:~:text=Kotlin%2FNative%20will%20support%20a%20very,cases%20for%20reflection%2C%20please)) | *Runtime:* N/A (must link in code). <br>*Compile-time:* Manual wiring or code generation (expect/actual or KSP).                                                                                                                                                                                                                                                                                                                                                                              | Ktor engine selection uses static linkage on Native ([Engines - 客户端 - Ktor](https://ktor.kotlincn.net/clients/http-client/engines.html#:~:text=In%20the%20case%20of%20the,the%20artifacts%20you%20have%20included)); kotlinx.serialization generates code for serializers (no reflection needed) ([Reflection? - Native - Kotlin Discussions](https://discuss.kotlinlang.org/t/reflection/4054#:~:text=Thanks%20for%20feedback%21)).  |
| **Kotlin/JS**         | Partially (via JS engine)                                                                                                                                                                                                    | *Runtime:* dynamic `import()` of modules ([import() - JavaScript - MDN Web Docs - Mozilla](https://developer.mozilla.org/en-US/docs/Web/JavaScript/Reference/Operators/import#:~:text=The%20import,ECMAScript%20module%20asynchronously%20and%20dynamically)) or evaluating scripts. <br>*Compile-time:* Bundle plugins or use declarations in code.                                                                                                                                          | Often uses explicit plugin registration (when module is imported).                                                                                                                                                                                                                                                                                                                                                                    |
| **Kotlin/Wasm**       | No (WASM is AOT-compiled)                                                                                                                                                                                                    | *Runtime:* Not available (could host multiple modules via JS). <br>*Compile-time:* Include all needed implementations in the build.                                                                                                                                                                                                                                                                                                                                                           | Still evolving target; likely similar to Native in limitations.                                                                                                                                                                                                                                                                                                                                                                       |
| **Swift (iOS/macOS)** | No first-class support                                                                                                                                                                                                       | *Runtime:* `dlopen` and manually query symbols (advanced). <br>*Compile-time:* Include frameworks, use protocols; manual registration.                                                                                                                                                                                                                                                                                                                                                        | iOS disallows loading new code at runtime (security); Swift forums discussing possible plugin API ([Swift dynamic loading API - Discussion - Swift Forums](https://forums.swift.org/t/swift-dynamic-loading-api/39495#:~:text=Absolutely,both%20hugely%20successful%20systems)).                                                                                                                                                      |
| **Rust**              | No (no reflection, dynamic linking unstable)                                                                                                                                                                                 | *Runtime:* `libloading` crate to load .so and call known entry points. <br>*Compile-time:* Macros or build scripts to include plugin code.                                                                                                                                                                                                                                                                                                                                                    | `plugin` crates (e.g. dynamic_plugin ([dynamic_plugin - Rust](https://docs.rs/dynamic-plugin#:~:text=In%20many%20pieces%20of%20software%2C,like%20systems%20are%20often%20used))); Often simpler to static link plugins into one binary ([plugin package - plugin - Go Packages](https://pkg.go.dev/plugin#:~:text=,certain%20flags%20and%20environment%20variables)).                                                                |
| **.NET (C#)**         | Yes (rich reflection API)                                                                                                                                                                                                    | *Runtime:* Reflection scanning of assemblies, MEF attributes ([Dynamic Plugin Loading Using MEF - Foci Solutions](https://www.focisolutions.com/2017/11/dynamic-plugin-loading-using-mef/#:~:text=The%20Managed%20Extensibility%20Framework%20,and%20inversion%20of%20control%20patterns)). <br>*Compile-time:* Source generators to register types.                                                                                                                                          | MEF for drop-in extensions ([Dynamic Plugin Loading Using MEF - Foci Solutions](https://www.focisolutions.com/2017/11/dynamic-plugin-loading-using-mef/#:~:text=The%20Managed%20Extensibility%20Framework%20,and%20inversion%20of%20control%20patterns)); Unity game engine uses assembly scanning for plugins.                                                                                                                       |
| **JavaScript/TS**     | Yes (dynamic nature of JS)                                                                                                                                                                                                   | *Runtime:* `require()` or `import()` modules by name; execute plugin code. <br>*Compile-time:* (N/A, since JS is interpreted/bundled).                                                                                                                                                                                                                                                                                                                                                        | E.g. VSCode loads extension packages dynamically; Webpack can code-split features for later loading.                                                                                                                                                                                                                                                                                                                                  |
| **Go**                | Limited (plugin pkg is OS-limited) ([plugin package - plugin - Go Packages](https://pkg.go.dev/plugin#:~:text=,applications%20intended%20to%20be%20portable))                                                                | *Runtime:* `plugin.Open` to load `.so` (Linux/mac only) ([plugin package - plugin - Go Packages](https://pkg.go.dev/plugin#:~:text=,applications%20intended%20to%20be%20portable)). <br>*Compile-time:* Use `init()` functions and blank import for static plugin registration ([Design patterns in Go's database/sql package - Eli Bendersky's website](https://eli.thegreenplace.net/2019/design-patterns-in-gos-databasesql-package/#:~:text=The%20trick%20is%20in%20the,blank%20import)). | `database/sql` driver registration via init ([Design patterns in Go's database/sql package - Eli Bendersky's website](https://eli.thegreenplace.net/2019/design-patterns-in-gos-databasesql-package/#:~:text=The%20trick%20is%20in%20the,blank%20import)); Go plugins considered fragile for general use ([plugin package - plugin - Go Packages](https://pkg.go.dev/plugin#:~:text=,load%20dangerous%20or%20untrusted%20libraries)). |

## Runtime vs Compile-Time Discovery: Trade-offs

There is an inherent trade-off between doing discovery at runtime versus at compile-time (or build-time):

- **Runtime Discovery Advantages:**
    - **Extensibility Without Recompile:** New plugins or modules can be added by users or third parties without rebuilding the application.
      This is crucial for applications meant to be platforms (e.g., an IDE, browser, or server where users can drop in extensions). Java’s
      ServiceLoader and .NET’s MEF were designed for this
      use-case ([Implementing Plugins with Java's Service Provider Interface](https://reflectoring.io/service-provider-interface/#:~:text=The%20Service%20Provider%20Interface%20was,to%20make%20applications%20more%20extensible)) ([Dynamic Plugin Loading Using MEF - Foci Solutions](https://www.focisolutions.com/2017/11/dynamic-plugin-loading-using-mef/#:~:text=The%20Managed%20Extensibility%20Framework%20,and%20inversion%20of%20control%20patterns)).
    - **Loose Coupling:** The core code does not need direct references to implementations. This can enforce clean architecture boundaries (
      core only knows interface, implementations register themselves). It also means optional features can be truly optional at runtime (if
      absent, they’re just not loaded).

- **Runtime Discovery Disadvantages:**
    - **Platform Limitations:** As we saw, not all environments support it (Kotlin/Native, Wasm, iOS have no equivalent to dynamic class
      loading). Designing a multiplatform solution around runtime discovery means you need alternative plans for those platforms (often
      negating the benefit).
    - **Performance Overhead:** Scanning for services can add startup time and memory overhead. For example, Ktor notes that using
      ServiceLoader on Android (a slower device) can hurt initialization
      time ([ktor/ktor-client/ktor-client-core/common/src/io/ktor/client/HttpClient.kt at main · ktorio/ktor · GitHub](https://github.com/ktorio/ktor/blob/main/ktor-client/ktor-client-core/common/src/io/ktor/client/HttpClient.kt#:~:text=%2A%20,Default%20Engine)).
      Similarly, reflection-based DI in Android apps was replaced by compile-time DI (Dagger/Hilt) to avoid slow cold starts.
    - **Complexity and Error Handling:** Dynamically loaded code can fail in new ways – missing dependencies, version mismatches, etc.
      Testing becomes harder because you might have to simulate missing or extra plugins. Also unloading plugins cleanly (to update or
      remove at runtime) is notoriously difficult (Java can do it with custom class loaders, .NET with AssemblyLoadContext, but it’s
      advanced).
    - **Security:** Loading unknown code at runtime can be a security risk. Many platforms either disallow it (iOS) or strongly caution
      about untrusted plugins. Even in Go, an official warning is that plugin mechanisms could be exploited by attackers if not carefully
      controlled ([plugin package - plugin - Go Packages](https://pkg.go.dev/plugin#:~:text=,load%20dangerous%20or%20untrusted%20libraries)) ([plugin package - plugin - Go Packages](https://pkg.go.dev/plugin#:~:text=,exactly%20the%20same%20source%20code)).

- **Compile-Time (Static) Discovery Advantages:**
    - **Performance and Size:** By resolving everything at compile time, you can strip out unused implementations and avoid doing work at
      startup. Tree-shaking and dead code elimination can remove unused providers, which is great for minimizing binary size (important in
      mobile and Web). The application also knows exactly what’s available, so no need for iterative reflection at runtime.
    - **Simplicity and Reliability:** There’s less “magic”. If a plugin is not linked in, it’s not there – no surprise. If it is included,
      it’s been compiled together, so compatibility is ensured (no class version conflicts). Tools like KSP (Kotlin Symbol Processing) or
      annotation processors can generate code to register plugins, which is easier to debug than a reflective approach. For example, KSP was
      used in the KRouter library to collect all route handlers at build time, replacing a prior ServiceLoader
      approach ([KRouter 1.0 was released, supporting parameter injection and KMP cross-platform. | by ZhangKe | Medium](https://medium.com/@kezhang404/krouter-1-0-was-released-supporting-parameter-injection-and-kmp-cross-platform-7cdfd2cd1759#:~:text=Due%20to%20time%20constraints%2C%20the,to%20the%20Maven%20Central%20Repository)) ([KRouter 1.0 was released, supporting parameter injection and KMP cross-platform. | by ZhangKe | Medium](https://medium.com/@kezhang404/krouter-1-0-was-released-supporting-parameter-injection-and-kmp-cross-platform-7cdfd2cd1759#:~:text=only%20barely%20functional,to%20the%20Maven%20Central%20Repository)).
      This eliminated the need for dynamic lookup, and made the library KMP-friendly.
    - **Works in AOT contexts:** Static approaches are the only option on platforms like Native and Wasm. By using code generation or manual
      listing of implementations, you ensure the multiplatform code runs everywhere. Kotlin’s kotlinx.serialization is a shining example –
      instead of relying on reflection to find serializers (like Jackson or Gson on JVM), it uses a compiler plugin to generate serializer
      objects for each data class. This way, serialization works on Native and JS (where reflection is not
      available) ([Reflection? - Native - Kotlin Discussions](https://discuss.kotlinlang.org/t/reflection/4054#:~:text=Thanks%20for%20feedback%21)).

- **Compile-Time Discovery Disadvantages:**
    - **Less Flexible Extensibility:** If you want to add a new plugin, you typically have to recompile or at least relink the application.
      This is acceptable for many cases (especially in mobile or backend services where you deploy a new build for updates), but it’s not
      suitable for user-installed plugins in an already-shipped app.
    - **Build Complexity:** Using code generators or annotation processors adds complexity to the build process. There’s more tooling and
      the potential for build-time errors. Also, the development workflow for plugin authors is less straightforward – instead of just
      implementing an interface and packaging a JAR, they might need to run a Gradle plugin or KSP to register their implementation.
    - **Coupling at Build Time:** The application needs to know (or be configured with) all implementations ahead of time. This could make
      it harder to produce a truly modular release where third parties can contribute independently. For example, if you want a plugin
      ecosystem where anyone can write a plugin without access to your source, a pure compile-time approach means you’d have to provide some
      way for their plugin to hook in – perhaps via a code-gen convention or a configuration file that is read at runtime (which edges back
      into dynamic territory).

In practice, many frameworks adopt a **hybrid approach**: use compile-time generation for the common case and allow runtime loading for
truly external extensions. For instance, **.NET** can use source generators to register known parts but still allow loading an external
assembly for extra plugins in enterprise scenarios. **Kotlin Multiplatform** may evolve similarly: encourage compile-time patterns for most
uses, while possibly adding a limited runtime discovery for platforms that support it (JVM/JS) with fallback requirements for others.

## Kotlin Multiplatform: Existing Tools and Patterns

Even without a built-in ServiceLoader, Kotlin developers have created solutions or use patterns to handle multi-implementation scenarios:

- **ServiceLoader on JVM + Expect/Actual:** One pragmatic approach in a KMP library is to use ServiceLoader on JVM, but on other platforms
  require explicit registration. A library can define a common interface and on JVM provide a function that internally does
  `ServiceLoader.load` to get implementations. On Native or JS, that function could instead just return an empty list or a default
  implementation, expecting the user to have manually registered one. This isn’t ideal, but it allows using the same API in common code. The
  downside is inconsistency across platforms (the library must document that, say, “on Native, you must call `register(MyImpl)` before using
  this, whereas on JVM it auto-discovers”).

- **KSP (Kotlin Symbol Processing) and Annotation Processors:** KSP is a Kotlin-first alternative to annotation processing that works for
  multiplatform projects. With KSP, you can scan the Kotlin code at compile time and generate additional code. For example, one could write
  a KSP processor that finds all classes annotated with `@MyService` and then generates a `ServiceRegistry` object that contains a list of
  those classes. The generated registry can then be used at runtime to iterate through implementations (all hard-coded, so it works without
  reflection). This technique was used by **KRouter**, as mentioned: they replaced a runtime ServiceLoader with a KSP-based
  collector ([KRouter 1.0 was released, supporting parameter injection and KMP cross-platform. | by ZhangKe | Medium](https://medium.com/@kezhang404/krouter-1-0-was-released-supporting-parameter-injection-and-kmp-cross-platform-7cdfd2cd1759#:~:text=Due%20to%20time%20constraints%2C%20the,to%20the%20Maven%20Central%20Repository)) ([KRouter 1.0 was released, supporting parameter injection and KMP cross-platform. | by ZhangKe | Medium](https://medium.com/@kezhang404/krouter-1-0-was-released-supporting-parameter-injection-and-kmp-cross-platform-7cdfd2cd1759#:~:text=only%20barely%20functional,to%20the%20Maven%20Central%20Repository)).
  Another potential use is implementing a multiplatform `AutoService` (similar to Google’s auto-service for Java) – in fact, one could write
  a KSP plugin to generate the necessary `META-INF/services` files for JVM and similar registry for Native/JS. KSP runs on each platform
  module, so it can generate platform-specific glue as needed. The downside is plugin authors need to apply the KSP processor.

- **Koin and DI frameworks:** **Koin** (a popular DI library in Kotlin) takes a manual approach that is inherently multiplatform. You
  declare modules in code, listing each binding: e.g., `module { single<Interface> { Impl() } }`. Because you explicitly provide the binding
  in code, there’s no discovery needed; Koin just uses maps under the hood. Koin works on Kotlin/Native as a result (with some threading
  restrictions). It shows that for many dependency injection needs, you don’t require a reflection-based approach – a bit of boilerplate (
  writing the module definitions) is enough, and can even be seen as a benefit for clarity. Other DI frameworks like **Kodein** or **Konform
  ** similarly avoid reflection. On the other hand, **Dagger** (famous on Android) is an example of compile-time generation for DI – it uses
  annotation processing (KAPT) to generate factories, and it could theoretically be adapted to KMP by using KSP for the Native side, though
  Dagger itself doesn’t target K/Native.

- **Compiler Plugins for Kotlin:** Beyond KSP, one could write a full Kotlin Compiler Plugin that automatically registers implementations.
  For example, a compiler plugin could identify all subclasses of a sealed interface and generate a function that returns them. The Kotlin
  serialization plugin is a model of this – it finds all classes with `@Serializable` and generates code accordingly. One could imagine an
  annotation `@AutoService` in KMP that a compiler plugin recognizes and then generates an `actual object ServiceRegistry` for each target.
  This is an advanced solution and requires maintaining a compiler plugin, but it’s doable. JetBrains might eventually provide something
  like this as an officially supported feature (especially if the YouTrack issue gains traction).

- **Manual Patterns in Multiplatform Projects:** Some projects simply structure their code to avoid needing a general ServiceLoader. For
  example, you might have a common interface and provide different implementations in different platform source sets (not as separate
  libraries, but as part of the project). The build can include or exclude certain implementations based on gradle properties. If truly
  dynamic loading isn’t needed, this is often the simplest: just use good old dependency inversion and pass the implementation to the code
  that needs it. Often, adding a new platform-specific implementation means adding a dependency on that implementation’s module and perhaps
  a few lines to wire it up – which is not overly burdensome unless you expected third-party unknown extensions.

- **Existing Multiplatform Libraries Avoiding the Issue:** It’s worth noting that many KMP libraries sidestep the need for dynamic discovery
  entirely. For instance, **kotlinx.coroutines** or **kotlinx.datetime** might have platform-specific implementations (like different
  dispatchers or timezone rules per platform), but these are selected via expect/actual or factory functions, not via scanning the
  classpath. In many cases, you simply don’t need a ServiceLoader if you plan the architecture to know about implementations via dependency
  injection (in the broader sense of passing dependencies) or configuration.

## Is a Multiplatform ServiceLoader Needed?

After examining all of the above, we can draw some conclusions:

- **For truly extensible *applications*** (where end-users or external developers provide plugins), a dynamic discovery mechanism is very
  useful. If Kotlin Multiplatform is to be used for building such applications (for example, a multiplatform IDE or a multiplatform game
  engine), then having a common abstraction to load plugins would be valuable. Without it, developers have to implement their own plugin
  loading for each platform (and some platforms might simply not support it, meaning those targets can’t be extensible in the same way).
- **For libraries and frameworks**, the need is a bit mixed. Some library authors (like the Ktor team, or the author of the YouTrack issue
  KT-53056) clearly encountered scenarios where multiple implementations need to be picked up. A built-in solution could simplify their
  work. It could also prevent each library from writing slightly different codegen or registration logic. On the other hand, many libraries
  manage fine with explicit registration or Gradle-time wiring. Kotlin’s philosophy often leans towards explicit wiring (e.g., the decision
  not to include reflection on Native was intentional to encourage efficient
  patterns ([Reflection? - Native - Kotlin Discussions](https://discuss.kotlinlang.org/t/reflection/4054#:~:text=Kotlin%2FNative%20will%20support%20a%20very,cases%20for%20reflection%2C%20please))).

- **Cross-platform Consistency:** If Kotlin were to introduce a multiplatform ServiceLoader-like feature, it would likely have to be a
  *compile-time* or build-time mechanism under the hood. For example, it could be an annotation that, on JVM, generates a META-INF/services
  file, on Native/JS generates a registry object, and provides a common API to query services. That would give the illusion of a uniform
  ServiceLoader, but actually use static lists on platforms that need it. This is feasible (community libraries or KSP processors could even
  implement this pattern). The trade-off is that it moves the problem to build time (which is generally a good thing for KMP). It would be
  similar to how Kotlin serialization works uniformly by doing per-platform code generation.

- **When It’s Unnecessary:** If an application is not meant to be extended by third parties at runtime, you might not need a dynamic
  discovery at all. Explicitly declaring what implementations to use leads to clearer, more maintainable code in many cases. For example, in
  a typical mobile app, you wouldn’t let someone drop in a new module at runtime – you’d ship a new version of the app with that code. Thus,
  a ServiceLoader mechanism might be overkill; it’s simpler to just instantiate the classes you need. Even on the JVM, many modern
  frameworks prefer explicit registration or classpath scanning limited to known assemblies rather than a free-for-all search, to avoid
  accidental classpath issues.

- **Footprint and Performance:** Especially in constrained environments (mobile, WebAssembly), avoiding a generalized dynamic loading can
  keep the binary size lower (since you don’t need to include metadata for all classes or keep reflection capabilities) and startup faster.
  Kotlin/Native’s lack of reflection is partly to ensure binaries are lean and initialization is predictable. If KMP introduced something
  like ServiceLoader that required e.g. bundling a list of classes, it would need to ensure it doesn’t bloat release binaries when not used.

In summary, **Kotlin Multiplatform doesn’t strictly *need*** a Java-like ServiceLoader in all cases, but providing a **standard,
multiplatform way to register and discover implementations could greatly benefit certain use cases**. It would reduce boilerplate for
library authors who now resort to KSP or manual maps, and it would enable a more “write once, run anywhere” approach to plugin-style
architectures in Kotlin. The decision comes down to balancing flexibility with the complexity it introduces on platforms that aren’t
naturally suited to it.

## Conclusion

Dynamic service discovery is a powerful feature for designing extensible systems, but its implementation is highly dependent on platform
capabilities. Java’s `ServiceLoader` and .NET’s MEF illustrate how runtime discovery can make architectures pluggable and
flexible ([Implementing Plugins with Java's Service Provider Interface](https://reflectoring.io/service-provider-interface/#:~:text=The%20Service%20Provider%20Interface%20was,to%20make%20applications%20more%20extensible)) ([Dynamic Plugin Loading Using MEF - Foci Solutions](https://www.focisolutions.com/2017/11/dynamic-plugin-loading-using-mef/#:~:text=The%20Managed%20Extensibility%20Framework%20,and%20inversion%20of%20control%20patterns)).
Kotlin Multiplatform, targeting a broader range of environments (including ones without reflection), leans towards compile-time strategies
to achieve similar goals. Real-world multiplatform projects use a mix of techniques: from **explicit registration** (Koin style, or Multik’s
`addEngine`), to **code generation** (KSP in KRouter, Kotlinx Serialization’s compiler plugin) to cover the gap on non-JVM targets. Other
languages show a spectrum of solutions, from Swift’s largely static approach to Go’s static init patterns, to Rust’s preference for macros
over runtime magic.

**Trade-offs** are evident – runtime discovery offers maximal flexibility at the cost of complexity and potential performance overhead,
whereas compile-time wiring offers efficiency and simplicity but requires foreknowledge of components. In many scenarios, a
ServiceLoader-like mechanism is not strictly necessary; careful architectural design or build-time tools can suffice or even be superior.
However, for certain Kotlin Multiplatform libraries (especially those aiming to be frameworks that others extend), the lack of a unified
discovery mechanism is a pain
point ([Engines - 客户端 - Ktor](https://ktor.kotlincn.net/clients/http-client/engines.html#:~:text=val%20client%20%3D%20HttpClient)). The
ongoing discussions in the community (e.g., KT-53056) suggest interest in developing a solution that works across KMP targets.

Going forward, we may see enhancements in Kotlin or its tooling to make multi-implementation discovery easier in a multiplatform-friendly
way. Until then, developers will continue employing the inventive mix of patterns described above to achieve plugin-like architectures in
KMP. The best approach depends on the specific needs: if runtime flexibility is paramount and you’re on JVM/JS, a ServiceLoader or dynamic
import might be your friend; if you need to support Native or prefer determinism, then invest in compile-time solutions that enumerate your
implementations ahead of time. Kotlin gives you the choice – and as the ecosystem matures, those choices will become even more ergonomic and
integrated.


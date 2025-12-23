package kxcli.api

public interface Command {
    public val name: String
    public val description: String

    // null -> success
    // string -> error
    public fun execute(args: Array<String>): String?

    public companion object
}

public interface KInterfaceSerializer<T> {
    // interfaceDescriptor


}

// At this moment it feels like there should be some HUGE abstraction which will connect DI, SPI(plugins) and RPC...

/*
JVM:
- `compiletime:
    - via `ServiceLoader.load(X, X.classloader)` - will be optimized by Android R8
    - JPMS with jlink is also similar
- `runtime`:
    - via custom classloader or moduleLayer (e.g. `ServiceLoader.load(X, createClassLoader(files))`

Native:
- `compiletime` - via `EagerInitialization
- `runtime`: (only desktop targets)
    - plugins should be built into shared library and then loaded via `loadPlugins(files)`
    - plugins should expose some function like `kotlin_provide_plugins(output_pointer)`
    - C structs with function pointers should be generated for every plugin interface
    - final application is built into an executable

JS:
- `compiletime` - via `EagerInitialization
- `runtime`: (both browser and nodejs)
    - plugins should be built into some JS library and then loaded via `loadPlugins(files/urls)`
    - plugins should expose some function like `kotlin_provide_plugins(builder: XXX)`
    - there should be JS interfaces/classes/objects with function generated for every plugin interface
    - in addition, it might be possible to use workers to isolate plugins, in this case, some mini RPC should be in place to communicate with workers

WasmJS - almost the same as with JS
Also, as WasmJs and JS expose `.mjs` files they could be interconnected

WasmWasi (or plain Wasm for WasmJs):
- `compiletime` - via `EagerInitialization
- `runtime` - via wit (component model)
 */

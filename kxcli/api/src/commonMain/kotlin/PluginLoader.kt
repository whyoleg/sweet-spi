package kxcli.api

import kotlin.reflect.*

@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.BINARY)
public annotation class Pluggable

@Repeatable
@Target(AnnotationTarget.CLASS, AnnotationTarget.PROPERTY, AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.BINARY)
public annotation class Plugin(public val pluggable: KClass<*>)

// for plugin
@RequiresOptIn("This API is internal", RequiresOptIn.Level.ERROR)
public annotation class InternalPluginApi

@InternalPluginApi
public fun interface PluginProvider</*@Pluggable*/ T : Any> {
    public fun provide(): T
}

public interface PluginLoader</*@Pluggable*/ T : Any> {
    public fun load(): Sequence<T> // default + eager (a.k.a. compile-time)
}

//public expect class PluginLoader<T : Any> {
//    // default + eager (a.k.a. compile-time)
//    public fun load(): Sequence<T>
//
//    // decide on directories traversing? - probably it should traverse directories and nested directories? should we provide globs? other abstraction?
//    // - for jvm, constructs classpath from jars
//    // - for native (desktop), get .dylib/.so - should we cache those, in case one lib contains multiple services? :(
//    // - for js/wasmJs (nodejs), get local files
//    // TODO: that's nice, but to transfer Kotlin objects through this layer we will need some kind of serialization and RPC for native/js, for workers we will need `suspend` :)
//    public fun loadFromFiles(paths: List<String>): Sequence<T>
//
//    public companion object {
//        public inline fun <reified T : Any> of(): PluginLoader<T>
//    }
//}

// we can connect systems between app and plugins
//public expect open class PluginSystem {
//    public companion object Default : PluginSystem {
//        public inline fun <reified T : Any> load(): Sequence<T>
//
//        // from classloader
//        public fun fromFiles(paths: List<String>): PluginSystem
//    }
//}

//public fun PluginSystem(paths: List<String>): PluginSystem = TODO()

// TODO: how to reset/reload PluginSystem in multiplatform?

public object InternalPluginSystem {
    private val storage = mutableMapOf<KClass<*>, MutableList<() -> Any>>()
    private var finalized = false

    public fun <T : Any> register(kClass: KClass<T>, provider: () -> T) {
        require(!finalized) { "PluginSystemBuilder already finalized" }
        storage.getOrPut(kClass, ::mutableListOf).add(provider)
    }
}

// if we ignore runtime discovery for now, then compile-time SPI allows:
// - collect interface implementations from dependencies - why it could be useful?
// - split api and impl in a project in different modules with auto wiring - this is just DI
// - it's enough, only if those `implementations` don't need configuration at all - is it enough?
// - conditional functionality? still, internal one, when dependency is present (kotlin.coroutines)
// - coroutines: main dispatcher and coroutine exception handler - again, optional, global behavior
// - overall: SPI idea is to affect GLOBAL behavior - is it a good or a bad thing?
// - platform specific behavior???
// - in klib backends, EagerInit is bad, because it's an additional root, which can not be removed if not used


// --------------------------------

@Pluggable
public interface LoggingBackend {
    // generated, platform specific
    public companion object {
//        public fun pluginLoader(): PluginLoader<LoggingBackend> = pluginLoader<LoggingBackend, LoggingBackendProvider>()
    }
}

// generated, ideally nested class
@InternalPluginApi
public fun interface LoggingBackendProvider : PluginProvider<LoggingBackend>

//public fun LoggingBackendPluginLoader(): PluginLoader<LoggingBackend> = pluginLoader<LoggingBackend, LoggingBackendProvider>()

// here, for JVM it will be enough
// for other targets, we need to specify, what we want: runtime of eager
@Plugin(LoggingBackend::class)
public object Slf4jLoggingBackend : LoggingBackend

// generated, ideally nested class
@InternalPluginApi
public class Slf4jLoggingBackendProvider : LoggingBackendProvider {
    override fun provide(): LoggingBackend = Slf4jLoggingBackend
}

// for jvm, generate file in META-INF

// for native, generate ONE `fun @CName("kotlinProviderPlugins") kotlinProvidePlugins(storage: COpaquePointer)`
// in this case, we will still need to use `@EagerInitialization` to collect all plugins, and then transfer them to the function above

// for js/wasmJs, generate one `@JsExport fun kotlinProvidePlugins(storage: XXX)`

// @EagerInitialization
internal val initializer: Unit = run {
    InternalPluginSystem.register(LoggingBackend::class) { Slf4jLoggingBackend } // same as in eager initialization
}

// for JVM compile-time - generated for Application
// same could be done in Android for Start-up thing-y
public fun PluginLoader.Companion.initializePluginLoaders(
    // provide classes for which to initialize?
) {
    // Gradle Plugin will collect all META-INF/services classes (used in kotlin spi)
    // will override `PluginLoader.load` behavior to not call ServiceLoader at all
    InternalPluginSystem.register(LoggingBackend::class) { Slf4jLoggingBackend } // same as in eager initialization
}

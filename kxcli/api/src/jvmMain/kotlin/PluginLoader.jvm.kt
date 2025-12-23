package kxcli.api

import sun.misc.*
import java.net.*
import java.util.*
import kotlin.io.path.*
import kotlin.reflect.*

public actual class PluginLoader<T : Any>
@PublishedApi
internal constructor(
    private val kClass: KClass<T>,
    private val serviceLoaderSequence: Sequence<T>,
) {
    public actual fun load(): Sequence<T> {
        return serviceLoaderSequence
    }

    public actual fun loadFromFiles(paths: List<String>): Sequence<T> = FilesSequence(kClass.java, paths)

    // jvm specific
    public fun loadFromClassLoader(classLoader: ClassLoader): Sequence<T> = Sequence {
        ServiceLoader.load(kClass.java, classLoader).iterator()
    }
    // public fun loadFromModuleLayer(moduleLayer: ModuleLayer): Sequence<T> - JDK9+

    public actual companion object {
        // TODO: this is not really true, as we need to have a wrapper to support JVM object/property/function loading
        public actual inline fun <reified T : Any> of(): PluginLoader<T> = PluginLoader(T::class, loadViaServiceLoader())
    }

    private class FilesSequence<T : Any>(
        private val klass: Class<T>,
        paths: List<String>,
    ) : Sequence<T> {
        private val classLoader = lazy { URLClassLoader(paths.map { Path(it).toUri().toURL() }.toTypedArray()) }

        // TODO: JDK9
        private val cleaner = Cleaner.create(this, CleanupAction(classLoader))

        private class CleanupAction(private val classLoader: Lazy<URLClassLoader>) : Runnable {
            override fun run() {
                if (classLoader.isInitialized()) classLoader.value.close()
            }
        }

        override fun iterator(): Iterator<T> {
            return ServiceLoader.load(klass, classLoader.value).iterator()
        }
    }
}

@PublishedApi
internal inline fun <reified T : Any> loadViaServiceLoader(): Sequence<T> = Sequence {
    // ServiceLoader should use specific call convention to be optimized by R8 on Android:
    // `ServiceLoader.load(X.class, X.class.getClassLoader()).iterator()`
    // source:
    // https://r8.googlesource.com/r8/+/refs/heads/main/src/main/java/com/android/tools/r8/ir/optimize/ServiceLoaderRewriter.java
    ServiceLoader.load(
        T::class.java,
        T::class.java.classLoader
    ).iterator()
}

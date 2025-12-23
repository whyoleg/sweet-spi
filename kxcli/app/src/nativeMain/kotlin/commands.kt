package kxcli

import kotlinx.cinterop.*
import kxcli.api.*
import kxcli.api.internal.cinterop.C_Command
import platform.posix.*
import kotlin.experimental.*
import kotlin.native.ref.*

@OptIn(ExperimentalForeignApi::class)
internal actual fun loadCommands(): List<Command> {
    // TODO: use kotlinx.io to list directory
    val files = listOf(
        "commands/libhello.dylib"
    )
//extern void kxcli_export_command(void* pointer);
    return files.map {
        // will be closed automatically (cleaner)
        val library = Library.open(it, RTLD_NOW)
        val arena = Arena()
        val struct = arena.alloc<C_Command>()
        // get struct
        library.getFunction<(COpaquePointer) -> Unit>("kxcli_export_command").invoke(struct.ptr)

        // TODO
        // println("Hey ${commandPointer.get().description}") - works fine
        // println(commandPointer.get().description) - throws `class kotlin.String cannot be cast to class kotlin.String`
        CommandWrapper(
            arena,
            Command.fromStruct(struct),
            library
        )
    }
}

// we need this class only to have cleaner, which will close library if Command will become unused
@OptIn(ExperimentalForeignApi::class, ExperimentalNativeApi::class)
private class CommandWrapper(
    arena: Arena,
    command: Command,
    private val library: Library,
) : Command by command {
    private val cleaner = createCleaner(arena) { it.clear() }
}

@OptIn(ExperimentalForeignApi::class, ExperimentalNativeApi::class)
internal class Library private constructor(private val handle: COpaquePointer) {
    private val cleaner = createCleaner(handle) { dlclose(it) }

    internal fun getSymbol(name: String): COpaquePointer? = dlsym(handle, name)

    inline fun <reified T : Function<*>> getFunction(name: String): CPointer<CFunction<T>> {
        return requireNotNull(getSymbol(name)?.reinterpret<CFunction<T>>()) { "Failed to find function '$name'" }
    }

    companion object {
        fun open(path: String, mode: Int): Library = Library(requireNotNull(dlopen(path, mode)) { "Failed to load library from '$path'" })
    }
}

package kxcli.api

import kotlinx.cinterop.*
import kxcli.api.internal.cinterop.*
import kotlin.experimental.ExperimentalNativeApi
import kotlin.native.ref.createCleaner

@OptIn(ExperimentalForeignApi::class)
public fun Command.placeStruct(pointer: CPointer<C_Command>) {
    cValue<C_Command> {
        user_data = CommandWrapper(this@placeStruct).pointer

        get_name = staticCFunction(::c_get_name)
        get_description = staticCFunction(::c_get_description)
        execute = staticCFunction(::c_execute)

        cleanup = staticCFunction(::c_clear)
    }.place(pointer)
}

@OptIn(ExperimentalForeignApi::class, ExperimentalNativeApi::class)
public fun Command.Companion.fromStruct(struct: C_Command): Command = object : Command {
    private val cleaner = createCleaner(struct) { it.cleanup?.invoke(it.ptr) }

    override val name: String get() = struct.get_name!!.invoke(struct.ptr)?.toKString() ?: ""
    override val description: String get() = struct.get_description!!.invoke(struct.ptr)?.toKString() ?: ""
    override fun execute(args: Array<String>): String? = memScoped {
        val array = allocArray<CPointerVar<ByteVar>>(args.size)
        args.forEachIndexed { index, string ->
            array[index] = string.cstr.ptr
        }
        return struct.execute!!.invoke(struct.ptr, array, args.size)?.toKString()
    }
}
//
//for stdlib
//internal inline fun <reified T : CVariable> NativePlacement.allocArrayOf(vararg elements: CValue<T>): CArrayPointer<T> {
//    val array = allocArray<T>(elements.size)
//    elements.forEachIndexed { index, element -> array[index] = element }
//    return array
//}
//
//internal inline operator fun <reified T : CVariable> CArrayPointer<T>.set(index: Int, value: CValue<T>) {
//    value.place(get(index).ptr)
//}


@OptIn(ExperimentalForeignApi::class)
private fun c_get_name(self: CPointer<C_Command>?): CPointer<ByteVar>? {
    val (command, arena) = CommandWrapper.unwrap(self) ?: return null
    return command.name.cstr.getPointer(arena)
}

@OptIn(ExperimentalForeignApi::class)
private fun c_get_description(self: CPointer<C_Command>?): CPointer<ByteVar>? {
    val (command, arena) = CommandWrapper.unwrap(self) ?: return null
    return command.description.cstr.getPointer(arena)
}

@OptIn(ExperimentalForeignApi::class)
private fun c_execute(self: CPointer<C_Command>?, args: CPointer<CPointerVar<ByteVar>>?, args_count: Int): CPointer<ByteVar>? {
    val (command, arena) = CommandWrapper.unwrap(self) ?: return null
    val arguments = Array(args_count) { args!![it]!!.toKString() }
    return command.execute(arguments)?.cstr?.getPointer(arena)
}

@OptIn(ExperimentalForeignApi::class)
private fun c_clear(self: CPointer<C_Command>?) {
    val (_, arena) = CommandWrapper.unwrap(self) ?: return
    arena.clear()
}

@OptIn(ExperimentalForeignApi::class)
private class CommandWrapper(private val command: Command) {
    private val arena = Arena()
    val pointer: COpaquePointer = StableRef.create(this).also {
        arena.defer(it::dispose)
    }.asCPointer()

    operator fun component1(): Command = command
    operator fun component2(): Arena = arena

    companion object {
        fun unwrap(self: CPointer<C_Command>?): CommandWrapper? = self?.pointed?.user_data?.asStableRef<CommandWrapper>()?.get()
    }
}

package kxcli.commands.hello

import kotlinx.cinterop.*
import kxcli.api.*
import kxcli.api.internal.cinterop.*
import kotlin.experimental.*

@OptIn(ExperimentalForeignApi::class, ExperimentalNativeApi::class)
@CName("kxcli_export_command")
public fun exportCommand(pointer: CPointer<C_Command>) {
    HelloCommand().placeStruct(pointer)
}

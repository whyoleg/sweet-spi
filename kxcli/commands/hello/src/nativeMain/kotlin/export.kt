package kxcli.commands.hello

import kxcli.api.*
import kotlin.experimental.*

@OptIn(ExperimentalNativeApi::class)
@CName("kxcli_command")
public fun command(): Command = HelloCommand()

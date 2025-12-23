package kxcli.commands.hello

import kxcli.api.*
import kotlin.js.*

@OptIn(ExperimentalJsExport::class)
@JsExport
public fun command(): JS_Command = Command.convertToJs(HelloCommand())

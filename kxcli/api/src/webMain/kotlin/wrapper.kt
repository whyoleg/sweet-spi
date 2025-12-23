package kxcli.api

import kotlin.js.*

@OptIn(ExperimentalWasmJsInterop::class)
public external interface JS_Command : JsAny {
    public var getName: () -> JsString
    public var getDescription: () -> JsString
    public var execute: (args: JsArray<JsString>) -> JsString?
}

@OptIn(ExperimentalWasmJsInterop::class)
public fun Command.Companion.convertToJs(command: Command): JS_Command = jsCommand(
    { command.name.toJsString() }
).apply {
//    getName = { command.name.toJsString() }
//    getDescription = { command.description.toJsString() }
//    execute = { args ->
//        command.execute(Array(args.length) { args[it].toString() })?.toJsString()
//    }
}

@OptIn(ExperimentalWasmJsInterop::class)
public fun Command.Companion.convertFromJs(command: JS_Command): Command = object : Command {
    override val name: String get() = command.getName().toString()
    override val description: String get() = command.getDescription().toString()
    override fun execute(args: Array<String>): String? = command.execute(args.map { it.toJsString() }.toJsArray())?.toString()
}

@OptIn(ExperimentalWasmJsInterop::class)
private fun jsCommand(
    getName: () -> JsString,
): JS_Command = js("{getName: getName}")

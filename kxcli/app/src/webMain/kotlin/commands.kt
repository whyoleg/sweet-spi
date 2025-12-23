package kxcli

import kxcli.api.*
import kotlin.coroutines.*
import kotlin.js.*

@OptIn(ExperimentalWasmJsInterop::class)
internal actual suspend fun loadCommands(): List<Command> {
    // this is the main lib
    val files = listOf(
        "/Users/Oleg.Yukhnevich/Projects/whyoleg/sweet-spi/kxcli/app/commands/wasm/sweet-spi-kxcli-command-hello.mjs",
        "/Users/Oleg.Yukhnevich/Projects/whyoleg/sweet-spi/kxcli/app/commands/js/sweet-spi-kxcli-command-hello.mjs",
//        "/Users/Oleg.Yukhnevich/Projects/whyoleg/sweet-spi/kxcli/app/commands/developmentLibrary/sweet-spi-kxcli-command-hello.mjs",
//        "/Users/Oleg.Yukhnevich/Projects/whyoleg/sweet-spi/kxcli/app/commands/productionLibrary/sweet-spi-kxcli-command-hello.mjs"
//        "commands/libhello-js/sweet-spi-kxcli-command-hello.mjs"
    )

    return files.map {
        Command.convertFromJs(
            suspendCoroutine { coroutine ->
                jsImport(it).then(
                    onFulfilled = {
                        coroutine.resume(it.unsafeCast<CommandHolder>().command())
                        null
                    },
                    onRejected = {
                        coroutine.resumeWithException(Throwable(it.toString()))
                        null
                    }
                )
            }
        )
    }
}

@OptIn(ExperimentalWasmJsInterop::class)
private fun jsImport(file: String): Promise<JsAny> = js("import(file)")

@OptIn(ExperimentalWasmJsInterop::class)
private external class CommandHolder : JsAny {
    fun command(): JS_Command
}

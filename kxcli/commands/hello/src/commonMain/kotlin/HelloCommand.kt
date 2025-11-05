package kxcli.commands.hello

import kxcli.api.*

internal class HelloCommand : Command {
    override val name: String get() = "hello"
    override val description: String
        get() = "prints `Hello, {arg}!"

    override fun execute(args: List<String>): String? = try {
        val arg = args.singleOrNull() ?: error("Please provide a single argument")
        println("Hello, $arg!")
        null
    } catch (cause: Throwable) {
        cause.toString()
    }
}

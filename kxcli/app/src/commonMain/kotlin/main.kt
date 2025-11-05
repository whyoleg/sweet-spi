package kxcli

import kxcli.api.*

public fun main(args: Array<String>) {
//    val command = args.firstOrNull() ?: error("No command specified")
//    val commandArgs = args.drop(1)
    val commands = loadCommands()

    commands.forEach {
        println("Command: ${it.name}")
        println("Description: ${it.description}")
        it.execute(listOf("Oleg"))
    }
}

internal expect fun loadCommands(): List<Command>

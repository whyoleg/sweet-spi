package kxcli

import kxcli.api.*

public suspend fun main(args: Array<String>) {
//    val command = args.firstOrNull() ?: error("No command specified")
//    val commandArgs = args.drop(1)
    val commands = loadCommands()

    commands.forEach {
        println("Command: ${it.name}")
        println("Description: ${it.description}")
        it.execute(arrayOf("Oleg"))
    }
}

internal expect suspend fun loadCommands(): List<Command>

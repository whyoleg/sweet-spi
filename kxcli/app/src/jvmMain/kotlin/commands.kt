package kxcli

import kxcli.api.*
import java.net.*
import java.util.*
import kotlin.io.path.*

internal actual fun loadCommands(): List<Command> {
    println(Path("commands"))
    val jars = Path("commands").listDirectoryEntries("*.jar")
    val classLoader = URLClassLoader(jars.map { it.toUri().toURL() }.toTypedArray())
    return ServiceLoader.load(Command::class.java, classLoader).toList()
}

/*
 * Copyright (c) 2026 Oleg Yukhnevich. Use of this source code is governed by the Apache 2.0 license.
 */

package dev.whyoleg.sweetspi.gradle.jpms

internal fun moduleInfoJava(
    name: String,
    isOpened: Boolean = false,
    block: ModuleInfoJavaBuilder.() -> Unit,
): String = ModuleInfoJavaBuilder(name, isOpened).apply(block).build()

public class ModuleInfoJavaBuilder internal constructor(
    private val moduleName: String,
    private val isOpened: Boolean,
) {
    private class RequiresInfo(
        val isTransitive: Boolean,
        val isStatic: Boolean,
    )

    private class ExportsInfo(
        val exportedTo: List<String>,
    )

    private class OpensInfo(
        val openedTo: List<String>,
    )

    private class ProvidesInfo(
        val providedWith: String,
    )

    private val requires = mutableMapOf<String, RequiresInfo>()
    private val exports = mutableMapOf<String, ExportsInfo>()
    private val opens = mutableMapOf<String, OpensInfo>()
    private val uses = mutableSetOf<String>()
    private val provides = mutableMapOf<String, ProvidesInfo>()

    // modules
    public fun requires(moduleName: String) {
        require(moduleName.isNotBlank()) { "Module name cannot be blank" }
        require(moduleName !in requires) { "Module '$moduleName' is already required" }
        requires[moduleName] = RequiresInfo(isTransitive = false, isStatic = false)
    }

    public fun requiresTransitive(moduleName: String) {
        require(moduleName.isNotBlank()) { "Module name cannot be blank" }
        require(moduleName !in requires) { "Module '$moduleName' is already required" }
        requires[moduleName] = RequiresInfo(isTransitive = true, isStatic = false)
    }

    public fun requiresStatic(moduleName: String) {
        require(moduleName.isNotBlank()) { "Module name cannot be blank" }
        require(moduleName !in requires) { "Module '$moduleName' is already required" }
        requires[moduleName] = RequiresInfo(isTransitive = false, isStatic = true)
    }

    // packages
    public fun exports(packageName: String) {
        require(packageName.isNotBlank()) { "Package name cannot be blank" }
        require(packageName !in exports) { "Package '$packageName' is already exported" }
        exports[packageName] = ExportsInfo(exportedTo = emptyList())
    }

    public fun exportsTo(packageName: String, moduleName: String) {
        require(packageName.isNotBlank()) { "Package name cannot be blank" }
        require(moduleName.isNotBlank()) { "Module name cannot be blank" }
        require(packageName in exports) { "Package '$packageName' is not exported" }
        exports[packageName] = ExportsInfo(exportedTo = listOf(moduleName))
    }

    public fun exportsTo(packageName: String, moduleNames: List<String>) {
        require(packageName.isNotBlank()) { "Package name cannot be blank" }
        require(moduleNames.isNotEmpty()) { "Module names cannot be empty" }
        require(moduleNames.all { it.isNotBlank() }) { "Module names cannot be blank" }
        require(packageName in exports) { "Package '$packageName' is not exported" }
        exports[packageName] = ExportsInfo(exportedTo = moduleNames)
    }

    public fun opens(packageName: String) {
        require(packageName.isNotBlank()) { "Package name cannot be blank" }
        require(packageName !in opens) { "Package '$packageName' is already opened" }
        opens[packageName] = OpensInfo(openedTo = emptyList())
    }

    public fun opensTo(packageName: String, moduleName: String) {
        require(packageName.isNotBlank()) { "Package name cannot be blank" }
        require(moduleName.isNotBlank()) { "Module name cannot be blank" }
        require(packageName in opens) { "Package '$packageName' is not opened" }
        opens[packageName] = OpensInfo(openedTo = listOf(moduleName))
    }

    public fun opensTo(packageName: String, moduleNames: List<String>) {
        require(packageName.isNotBlank()) { "Package name cannot be blank" }
        require(moduleNames.isNotEmpty()) { "Module names cannot be empty" }
        require(moduleNames.all { it.isNotBlank() }) { "Module names cannot be blank" }
        require(packageName in opens) { "Package '$packageName' is not opened" }
        opens[packageName] = OpensInfo(openedTo = moduleNames)
    }

    // services
    public fun uses(className: String) {
        require(className.isNotBlank()) { "Class name cannot be blank" }
        uses += className
    }

    public fun provides(serviceClassName: String, implementationClassName: String) {
        require(serviceClassName.isNotBlank()) { "Service class name cannot be blank" }
        require(implementationClassName.isNotBlank()) { "Implementation class name cannot be blank" }
        provides[serviceClassName] = ProvidesInfo(providedWith = implementationClassName)
    }

    // returns content of `module-info.java`
    internal fun build(): String = buildString {
        if (isOpened) append("open ")
        append("module ").append(moduleName).appendLine(" {")
        requires.forEach { (name, info) ->
            append("    requires ")
            if (info.isTransitive) append("transitive ")
            if (info.isStatic) append("static ")
            append(name)
            appendLine(";")
        }
        exports.forEach { (name, info) ->
            append("    exports ").append(name)
            if (info.exportedTo.isNotEmpty()) {
                append(" to ")
                info.exportedTo.joinTo(this, ", ")
            }
            appendLine(";")
        }
        opens.forEach { (name, info) ->
            append("    opens ").append(name)
            if (info.openedTo.isNotEmpty()) {
                append(" to ")
                info.openedTo.joinTo(this, ", ")
            }
            appendLine(";")
        }
        uses.forEach { name ->
            append("    uses ").append(name).appendLine(";")
        }
        provides.forEach { (name, info) ->
            append("    provides ").append(name).append(" with ").append(info.providedWith).appendLine(";")
        }
        append("}")
    }
}

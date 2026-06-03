/*
 * Copyright (c) 2026 Oleg Yukhnevich. Use of this source code is governed by the Apache 2.0 license.
 */

package dev.whyoleg.sweetspi.compiler.ir

import org.jetbrains.kotlin.name.*
import java.nio.file.*
import kotlin.io.path.*

// JVM resource outputs: `META-INF/services` files for the runtime ServiceLoader, and the
// `module-info.properties` descriptor (# exports / # uses / # provides) consumed by the gradle plugin's
// ModuleInfoJavaBuilder.

internal fun generateJvmServices(
    resourcesPath: Path,
    generatedProviderImplementations: Map<String, List<String>>,
) {
    // recreate all resources
    resourcesPath.toFile().deleteRecursively()
    generatedProviderImplementations.forEach { (serviceId, serviceImplIds) ->
        resourcesPath.resolve("META-INF/services/${serviceId}").createParentDirectories().writeText(
            serviceImplIds.joinToString("\n")
        )
    }
}

// # exports
// org.example.y
// # uses
// org.example.y.Service - e.g. calls to kotlin and java `ServiceLoader` methods (collected in M2)
// # provides
// org.example.y.Service=org.example.y.ServiceImpl
internal fun generateModuleInfoProperties(
    moduleInfoPropertiesPath: Path,
    collectedPackages: List<String>,
    collectedServiceLoaderUsages: List<String>,
    generatedProviderImplementations: Map<String, List<String>>,
) {
    val content = buildString {
        appendLine("# exports")
        collectedPackages.forEach(::appendLine)
        appendLine("# uses")
        collectedServiceLoaderUsages.forEach(::appendLine)
        appendLine("# provides")
        generatedProviderImplementations.forEach { (serviceId, serviceImplIds) ->
            serviceImplIds.forEach {
                append(serviceId).append('=').appendLine(it)
            }
        }
        appendLine()
    }

    moduleInfoPropertiesPath.writeText(content)
}

internal fun ClassId.toJvmString(): String {
    return if (packageFqName.isRoot) {
        relativeClassName.asString().replace('.', '$')
    } else {
        buildString {
            append(packageFqName.asString())
            append(".")
            append(relativeClassName.asString().replace('.', '$'))
        }
    }
}

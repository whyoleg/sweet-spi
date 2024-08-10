/*
 * Copyright (c) 2024 Oleg Yukhnevich. Use of this source code is governed by the Apache 2.0 license.
 */

package dev.whyoleg.sweetspi.gradle

import org.gradle.api.artifacts.*
import org.gradle.api.artifacts.dsl.*
import org.jetbrains.kotlin.gradle.dsl.*
import org.jetbrains.kotlin.gradle.plugin.*
import org.jetbrains.kotlin.gradle.plugin.mpp.*

public fun DependencyHandler.sweetSpiRuntime(): Dependency {
    return create("dev.whyoleg.sweetspi:sweetspi-runtime:${SweetSpiProperties.version}")
}

public fun DependencyHandler.sweetSpiProcessor(): Dependency {
    return create("dev.whyoleg.sweetspi:sweetspi-processor:${SweetSpiProperties.version}")
}

public fun KotlinDependencyHandler.sweetSpiRuntime(): Dependency = project.dependencies.sweetSpiRuntime()

public fun KotlinDependencyHandler.sweetSpiProcessor(): Dependency = project.dependencies.sweetSpiProcessor()

public fun KotlinProjectExtension.withSweetSpi(
    enabled: Boolean = true,
    compilationFilter: (KotlinCompilation<*>) -> Boolean = { it.name.endsWith("main", ignoreCase = true) },
) {
    when (this) {
        is KotlinSingleTargetExtension<*> -> target.withSweetSpi(enabled, compilationFilter)
        is KotlinMultiplatformExtension   -> targets.all { it.withSweetSpi(enabled, compilationFilter) }
    }
}

public fun KotlinTarget.withSweetSpi(
    enabled: Boolean = true,
    compilationFilter: (KotlinCompilation<*>) -> Boolean = { it.name.endsWith("main", ignoreCase = true) },
) {
    compilations.all {
        if (compilationFilter(it)) it.withSweetSpi(enabled)
    }
}

// TODO: support disabling
public fun KotlinCompilation<*>.withSweetSpi(@Suppress("UNUSED_PARAMETER") enabled: Boolean = true) {
    val configurationName = getKotlinConfigurationName(this) ?: return
    project.dependencies.apply {
        add(configurationName, sweetSpiProcessor())
        add(defaultSourceSet.implementationConfigurationName, sweetSpiRuntime())
    }
}

// tries to mimic KSP logic...
private fun getKotlinConfigurationName(compilation: KotlinCompilation<*>): String? {
    val isMain = compilation.name == KotlinCompilation.MAIN_COMPILATION_NAME
    // Note: on single-platform, the target name is conveniently set to "".
    val name = when {
        // skip, this will be dropped and unused now
        isMain && compilation is KotlinCommonCompilation -> return null
        isMain                                           -> {
            compilation.target.name
        }
        compilation is KotlinCommonCompilation           -> {
            compilation.defaultSourceSet.name + compilation.target.name.replaceFirstChar(Char::uppercase)
        }
        else                                             -> {
            compilation.defaultSourceSet.name
        }
    }
    return "ksp" + name.replaceFirstChar(Char::uppercase)
}

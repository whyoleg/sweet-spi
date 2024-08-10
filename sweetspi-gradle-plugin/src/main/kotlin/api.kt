/*
 * Copyright (c) 2024 Oleg Yukhnevich. Use of this source code is governed by the Apache 2.0 license.
 */

package dev.whyoleg.sweetspi.gradle

import org.gradle.api.artifacts.*
import org.gradle.api.artifacts.dsl.*
import org.jetbrains.kotlin.gradle.dsl.*
import org.jetbrains.kotlin.gradle.plugin.*
import org.jetbrains.kotlin.gradle.plugin.mpp.*

/**
 * This method returns a dependency object for the `sweetspi-processor` which can be used to manually add dependency on it.
 *
 * The version of the library is determined by the version of the plugin.
 *
 * Usage:
 * ```
 * // in build.gradle.kts
 *
 * // don't forget to add an import
 * import dev.whyoleg.sweetspi.gradle.*
 *
 * dependencies {
 *     // for kotlin-jvm projects
 *     ksp(sweetSpiProcessor())
 *     // for kotlin-multiplatform projects
 *     // Note: no need to add it to the `common` compilations, as it'd do nothing there
 *     add("kspJvm", sweetSpiProcessor())
 *     // if needed, `test` source sets support
 *     add("kspJvmTest", sweetSpiProcessor())
 *     // and for each other Kotlin target...
 *     add("kspLinuxX64Test", sweetSpiProcessor())
 * }
 * ```
 */
public fun DependencyHandler.sweetSpiProcessor(): Dependency {
    return create("dev.whyoleg.sweetspi:sweetspi-processor:${SweetSpiProperties.version}")
}

/**
 * This method returns a dependency object for the `sweetspi-runtime` which can be used to manually add dependency on it.
 *
 * The version of the library is determined by the version of the plugin.
 * This function is applicable for projects with `kotlin-jvm` plugin.
 *
 * Usage:
 * ```
 * // in build.gradle.kts
 *
 * // don't forget to add an import
 * import dev.whyoleg.sweetspi.gradle.*
 *
 * dependencies {
 *     implementation(sweetSpiRuntime())
 * }
 * ```
 */
public fun DependencyHandler.sweetSpiRuntime(): Dependency {
    return create("dev.whyoleg.sweetspi:sweetspi-runtime:${SweetSpiProperties.version}")
}


/**
 * This method returns a dependency object for the `sweetspi-runtime` which can be used to manually add dependency on it.
 *
 * The version of the library is determined by the version of the plugin
 * This function is applicable for projects with `kotlin-multiplatform` plugin.
 *
 * Usage:
 * ```
 * // in build.gradle.kts
 *
 * // don't forget to add an import
 * import dev.whyoleg.sweetspi.gradle.*
 *
 * kotlin {
 *     sourceSets {
 *         commonMain.dependencies {
 *             implementation(sweetSpiRuntime())
 *         }
 *     }
 * }
 * ```
 */
public fun KotlinDependencyHandler.sweetSpiRuntime(): Dependency = project.dependencies.sweetSpiRuntime()

/**
 * Adds the sweet-spi runtime and KSP processor dependencies to the compilations of all targets.
 *
 * It's possible to filter compilations which should receive those dependencies using [compilationFilter].
 * By default, only `main` compilations are affected
 *
 * Usage:
 * ```
 * // in build.gradle.kts
 *
 * // don't forget to add an import
 * import dev.whyoleg.sweetspi.gradle.*
 *
 * kotlin {
 *     // to add to `main` compilations only
 *     withSweetSpi()
 *
 *     // to add to `test` compilation
 *     withSweetSpi { it.name == "test" }
 * }
 * ```
 */
public fun KotlinProjectExtension.withSweetSpi(
    compilationFilter: (KotlinCompilation<*>) -> Boolean = { it.name.endsWith("main", ignoreCase = true) },
) {
    when (this) {
        is KotlinSingleTargetExtension<*> -> target.withSweetSpi(compilationFilter)
        is KotlinMultiplatformExtension   -> targets.all { it.withSweetSpi(compilationFilter) }
    }
}

/**
 * Adds the sweet-spi runtime and KSP processor dependencies to the compilations of this target.
 *
 * It's possible to filter compilations which should receive those dependencies using [compilationFilter].
 * By default, only `main` compilations are affected
 *
 * Usage:
 * ```
 * // in build.gradle.kts
 *
 * // don't forget to add an import
 * import dev.whyoleg.sweetspi.gradle.*
 *
 * kotlin {
 *     jvm {
 *         // to add to `main` compilations only
 *         withSweetSpi()
 *
 *         // to add to all compilations
 *         withSweetSpi { true }
 *     }
 * }
 * ```
 */
public fun KotlinTarget.withSweetSpi(
    compilationFilter: (KotlinCompilation<*>) -> Boolean = { it.name.endsWith("main", ignoreCase = true) },
) {
    compilations.all {
        if (compilationFilter(it)) it.withSweetSpi()
    }
}

/**
 * Adds the sweet-spi runtime and KSP processor dependencies to the specified Kotlin compilation.
 *
 * Usage:
 * ```
 * // in build.gradle.kts
 *
 * // don't forget to add an import
 * import dev.whyoleg.sweetspi.gradle.*
 *
 * kotlin {
 *     wasmJs {
 *         compilations.named("test") {
 *             withSweetSpi()
 *         }
 *     }
 * }
 * ```
 */
public fun KotlinCompilation<*>.withSweetSpi() {
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

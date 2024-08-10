/*
 * Copyright (c) 2024 Oleg Yukhnevich. Use of this source code is governed by the Apache 2.0 license.
 */

package dev.whyoleg.sweetspi.gradle

import org.gradle.api.*
import org.gradle.api.logging.*

/**
 * This plugin performs several checks that KSP is configured correctly.
 *
 * By default, this plugin performs the following checks:
 * - Ensures that the KSP (Kotlin Symbol Processing) Gradle Plugin is applied to your project.
 * - Ensures that the sweet-spi KSP processor (`sweetspi-processor` artifact) is added to at least one of the KSP configurations.
 *
 * If any of these checks fail, appropriate error messages will be logged to help you resolve the issue.
 *
 * This behavior can be suppressed by setting the following Gradle property to `true`:
 * - `dev.whyoleg.sweetspi.suppressGradleKspConfigurationChecker`
 *
 * Example of setting the property in `gradle.properties`:
 * ```properties
 * dev.whyoleg.sweetspi.suppressGradleKspConfigurationChecker=true
 * ```
 *
 * To simplify configuration of sweet-spi in project, take a look on [withSweetSpi] APIs,
 * which helps to add runtime and KSP processor dependencies to the appropriate configurations.
 *
 * Usage:
 * To apply this plugin, add the following code to your build.gradle[.kts] file:
 * ```
 * // in build.gradle.kts
 *
 * // don't forget to add an import
 * import dev.whyoleg.sweetspi.gradle.*
 *
 * plugins {
 *     // jvm or multiplatform
 *     kotlin("multiplatform")
 *     // Make sure to also apply the KSP plugin
 *     id("com.google.devtools.ksp")
 *     id("dev.whyoleg.sweetspi")
 * }
 *
 * kotlin {
 *     withSweetSpi()
 *     // declare kotlin targets
 * }
 * ```
 */
public abstract class SweetSpiPlugin : Plugin<Project> {
    private val logger = Logging.getLogger(SweetSpiPlugin::class.java)

    final override fun apply(target: Project) {
        val suppressGradleKspConfigurationChecker = target.providers
            .gradleProperty("dev.whyoleg.sweetspi.suppressGradleKspConfigurationChecker")
            .map(String::toBooleanStrict)
            .orNull
            ?: false // false by default

        if (suppressGradleKspConfigurationChecker) return

        var kspPluginApplied = false
        var kspProcessorAdded = false

        target.plugins.withId("com.google.devtools.ksp") {
            kspPluginApplied = true
            target.configurations.configureEach { configuration ->
                if (configuration.name.startsWith("ksp")) configuration.dependencies.whenObjectAdded {
                    if (
                        it.group == "dev.whyoleg.sweetspi" &&
                        it.name == "sweetspi-processor"
                    ) kspProcessorAdded = true
                }
            }
        }

        // TODO: better error messages
        target.afterEvaluate {
            if (!kspPluginApplied) logger.error(
                "KSP Gradle Plugin should be applied to use sweet-spi. " +
                        "Add 'com.google.devtools.ksp' plugin to your build, " +
                        "f.e by adding 'id(\"com.google.devtools.ksp\")' to 'plugins' section of the build.gradle[.kts] file"
            ) else if (!kspProcessorAdded) logger.error(
                "sweetspi-processor is not present in any KSP configuration. " +
                        "Use `withSweetSpi` API to add it automatically to main compilations or " +
                        "add `ksp(sweetSpiProcessor())` or `KSP_TARGET_SPECIFIC_CONFIGURATION_NAME(sweetSpiProcessor())`"
            )
        }
    }
}

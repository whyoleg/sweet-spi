/*
 * Copyright (c) 2024 Oleg Yukhnevich. Use of this source code is governed by the Apache 2.0 license.
 */

package dev.whyoleg.sweetspi.gradle

import org.gradle.api.*
import org.gradle.api.logging.*

public abstract class SweetSpiPlugin : Plugin<Project> {
    private val logger = Logging.getLogger(SweetSpiPlugin::class.java)

    final override fun apply(target: Project) {
        val validateGradleKspConfiguration = target.providers
            .gradleProperty("dev.whyoleg.sweetspi.validateGradleKspConfiguration")
            .map(String::toBooleanStrict)
            .orNull
            ?: true // true by default

        if (!validateGradleKspConfiguration) return

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

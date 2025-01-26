/*
 * Copyright (c) 2024-2025 Oleg Yukhnevich. Use of this source code is governed by the Apache 2.0 license.
 */

package dev.whyoleg.sweetspi.gradle

import org.gradle.api.*
import org.gradle.api.model.*
import org.gradle.api.provider.Provider
import org.jetbrains.kotlin.gradle.dsl.*
import org.jetbrains.kotlin.gradle.plugin.*
import javax.inject.*

/**
 * This plugin configures Kotlin compiler plugin for sweet-spi.
 *
 * Usage:
 * To apply this plugin, add the following code to your build.gradle[.kts] file:
 * ```
 * // in build.gradle.kts
 *
 * plugins {
 *     // jvm or multiplatform
 *     kotlin("multiplatform")
 *     id("dev.whyoleg.sweetspi")
 * }
 * ```
 */
public abstract class SweetSpiPlugin @Inject constructor(
    objectFactory: ObjectFactory,
) : KotlinCompilerPluginSupportPlugin {
    private val kotlinVersion = objectFactory.property(String::class.java)

    override fun apply(target: Project) {
        kotlinVersion.finalizeValueOnRead()

        target.plugins.withId("org.jetbrains.kotlin.jvm") {
            target.extensions.configure<KotlinJvmProjectExtension>("kotlin") {
                // TODO: there is some problem with opt-in...
                @Suppress("OPT_IN_USAGE", "OPT_IN_USAGE_ERROR")
                kotlinVersion.set(it.compilerVersion)

                it.sourceSets.named("main") { sourceSet ->
                    sourceSet.dependencies {
                        implementation("dev.whyoleg.sweetspi:sweetspi-runtime:${SweetSpiProperties.version}")
                    }
                }
            }
        }
        target.plugins.withId("org.jetbrains.kotlin.multiplatform") {
            target.extensions.configure<KotlinMultiplatformExtension>("kotlin") {
                // TODO: there is some problem with opt-in...
                @Suppress("OPT_IN_USAGE", "OPT_IN_USAGE_ERROR")
                kotlinVersion.set(it.compilerVersion)

                it.sourceSets.named("commonMain") { sourceSet ->
                    sourceSet.dependencies {
                        implementation("dev.whyoleg.sweetspi:sweetspi-runtime:${SweetSpiProperties.version}")
                    }
                }
            }
        }
    }

    override fun getCompilerPluginId(): String = "dev.whyoleg.sweetspi"

    override fun getPluginArtifact(): SubpluginArtifact = SubpluginArtifact(
        groupId = "dev.whyoleg.sweetspi",
        artifactId = "sweetspi-compiler-plugin",
        version = SweetSpiProperties.version + "-" + kotlinVersion.get()
    )

    override fun isApplicable(kotlinCompilation: KotlinCompilation<*>): Boolean = true
    override fun applyToCompilation(kotlinCompilation: KotlinCompilation<*>): Provider<List<SubpluginOption>> {
        // TODO: for JVM we should provide folder for resources
        return kotlinCompilation.target.project.provider { emptyList() }
    }
}

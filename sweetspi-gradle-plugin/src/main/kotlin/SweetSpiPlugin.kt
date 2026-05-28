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
    override fun apply(target: Project) {
        target.plugins.withId("org.jetbrains.kotlin.jvm") {
            target.extensions.configure<KotlinJvmProjectExtension>("kotlin") {
                it.sourceSets.named("main") { sourceSet ->
                    sourceSet.dependencies {
                        implementation("dev.whyoleg.sweetspi:sweetspi-runtime:${SweetSpiProperties.version}")
                    }
                }
            }
        }
        target.plugins.withId("org.jetbrains.kotlin.multiplatform") {
            target.extensions.configure<KotlinMultiplatformExtension>("kotlin") {
                it.sourceSets.named("commonMain") { sourceSet ->
                    sourceSet.dependencies {
                        implementation("dev.whyoleg.sweetspi:sweetspi-runtime:${SweetSpiProperties.version}")
                    }
                }
            }
        }
    }

    override fun getCompilerPluginId(): String = "dev.whyoleg.sweetspi"

    // TODO: support compiler plugin with different Kotlin versions in future
    override fun getPluginArtifact(): SubpluginArtifact = SubpluginArtifact(
        groupId = "dev.whyoleg.sweetspi",
        artifactId = "sweetspi-compiler-plugin",
        version = SweetSpiProperties.version
    )

    override fun isApplicable(kotlinCompilation: KotlinCompilation<*>): Boolean = true

    override fun applyToCompilation(kotlinCompilation: KotlinCompilation<*>): Provider<List<SubpluginOption>> {
        val project = kotlinCompilation.target.project
        return when (kotlinCompilation.target.platformType) {
            KotlinPlatformType.jvm, KotlinPlatformType.androidJvm -> {
                val resourcesPath = project.layout.buildDirectory.dir(
                    "generated/sweetspi/${kotlinCompilation.defaultSourceSet.name}/resources"
                )
                val moduleInfoPropertiesPath = project.layout.buildDirectory.file(
                    "generated/sweetspi/${kotlinCompilation.defaultSourceSet.name}/module-info.properties"
                )
                // cross-link compilation and resource processing tasks via resources dir output
                kotlinCompilation.compileTaskProvider.configure {
                    it.outputs.dir(resourcesPath).withPropertyName("sweetspi.resourcesPath")
                    // TODO: why it creates a directory???
//                    it.outputs.files(moduleInfoPropertiesPath).withPropertyName("sweetspi.moduleInfoPropertiesPath")
                }
                // TODO: check if it is fine, as we do generate resources after compilation, so maybe we should move this dependency elsewhere
                //  now `resourcesPath` looks like both an input and an output
                kotlinCompilation.defaultSourceSet.resources.srcDir(
                    kotlinCompilation.compileTaskProvider.map { resourcesPath.get() }
                )
                project.provider {
                    listOf(
                        SubpluginOption("resourcesPath", resourcesPath.get().toString()),
                        SubpluginOption("moduleInfoPropertiesPath", moduleInfoPropertiesPath.get().toString())
                    )
                }
            }
            else                                                  -> project.provider { emptyList() }
        }
    }
}

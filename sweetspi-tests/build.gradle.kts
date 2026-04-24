/*
 * Copyright (c) 2024-2026 Oleg Yukhnevich. Use of this source code is governed by the Apache 2.0 license.
 */

@file:Suppress("UnstableApiUsage", "HasPlatformType")

import org.gradle.api.file.*
import org.gradle.api.provider.*
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.process.*
import sweetbuild.*

plugins {
    id("sweetbuild.kotlin")
    alias(libs.plugins.kotlin.jvm)
}

kotlin {
    compilerOptions {
        optIn.add("kotlin.io.path.ExperimentalPathApi")
        freeCompilerArgs.add("-Xcontext-parameters")
    }
}

dependencies {
    testImplementation(gradleTestKit())
    testImplementation(kotlin("test-junit5"))
}

// Dev artifacts for tests resolution - inspired by https://github.com/adamko-dev/dev-publish-plugin

val devArtifacts by configurations.dependencyScope("devArtifacts")

val devArtifactsResolver by configurations.resolvable("devArtifactsResolver") {
    devArtifactAttributes(objects)
    extendsFrom(devArtifacts)
}

dependencies {
    devArtifacts(projects.sweetspiRuntime)
    devArtifacts(projects.sweetspiCompilerPlugin)
    devArtifacts(projects.sweetspiGradlePlugin)
}

tasks.test {
    useJUnitPlatform()
    jvmArgumentProviders.add(
        TestsArgumentProvider(
            devArtifactsDirectories = devArtifactsResolver.incoming.files,
            devArtifactsVersion = provider { project.version.toString() },
            kotlinVersion = libs.versions.kotlin.asProvider(),
        )
    )
}

class TestsArgumentProvider(
    private val devArtifactsDirectories: FileCollection,
    @get:Input val devArtifactsVersion: Provider<String>,
    @get:Input val kotlinVersion: Provider<String>,
) : CommandLineArgumentProvider {

    // we don't need to track ALL content because of flexible timestamps
    @get:PathSensitive(PathSensitivity.RELATIVE)
    @get:InputFiles
    val devArtifactsDirectoriesInput
        get() = devArtifactsDirectories.asFileTree.matching { exclude("**/maven-metadata.*") }

    private val devArtifactsDirectoriesList: Provider<String>
        get() = devArtifactsDirectories.elements.map { directories ->
            directories.joinToString(",") { it.asFile.canonicalFile.invariantSeparatorsPath }
        }

    override fun asArguments(): Iterable<String> = listOf(
        "-Dsweettests.dev-artifacts-directories=${devArtifactsDirectoriesList.get()}",
        "-Dsweettests.dev-artifacts-version=${devArtifactsVersion.get()}",
        "-Dsweettests.kotlin-version=${kotlinVersion.get()}",
    )
}

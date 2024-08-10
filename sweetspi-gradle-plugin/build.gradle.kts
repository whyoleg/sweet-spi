/*
 * Copyright (c) 2024 Oleg Yukhnevich. Use of this source code is governed by the Apache 2.0 license.
 */

import org.jetbrains.dokka.gradle.*
import org.jetbrains.kotlin.gradle.dsl.*

plugins {
    `java-gradle-plugin`
    id("sweetbuild.kotlin")
    id("sweetbuild.publication")
    id("sweetbuild.documentation")
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlinx.bcv)
    alias(libs.plugins.gradle.publish)
}

description = "sweet-spi Gradle plugin"

kotlin {
    explicitApi()
    compilerOptions {
        // gradle 8+
        languageVersion.set(KotlinVersion.KOTLIN_1_8)
        apiVersion.set(KotlinVersion.KOTLIN_1_8)
        // progressiveMode works only for latest kotlin version
        progressiveMode.set(false)
    }
}

dependencies {
    // TODO: remove `stdlib`
    compileOnly(libs.kotlin.gradle.plugin)
    compileOnly(libs.ksp.gradle.plugin)

    api(project.dependencies.platform(projects.sweetspiBom))
}

@Suppress("UnstableApiUsage")
gradlePlugin {
    website = "https://whyoleg.github.io/sweet-spi/"
    vcsUrl = "https://github.com/whyoleg/sweet-spi.git"
    plugins.register("sweetspi") {
        id = "dev.whyoleg.sweetspi"
        implementationClass = "dev.whyoleg.sweetspi.gradle.SweetSpiPlugin"

        displayName = "Sweet SPI plugin"
        description = "Sweet SPI is a plugin to setup SPI for Kotlin Multiplatform"
        tags.addAll("spi", "kotlin")
    }
}

tasks.processResources {
    val projectVersion = project.version
    inputs.property("version", projectVersion)
    filesMatching("sweetspi.properties") {
        expand("sweetspiVersion" to projectVersion)
    }
}

tasks.withType<DokkaTask>().configureEach {
    outputDirectory.set(rootDir.resolve("docs/gradle-plugin-api"))
}

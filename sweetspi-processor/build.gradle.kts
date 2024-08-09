/*
 * Copyright (c) 2024 Oleg Yukhnevich. Use of this source code is governed by the Apache 2.0 license.
 */

import com.vanniktech.maven.publish.*

plugins {
    id("sweetbuild.kotlin")
    id("sweetbuild.publication")
    alias(libs.plugins.kotlin.jvm)
}

description = "sweet-spi KSP processor"

mavenPublishing {
    configure(KotlinJvm())
}

dependencies {
    compileOnly(libs.ksp.api)
    implementation(libs.kotlinpoet.ksp)

    api(project.dependencies.platform(projects.sweetspiBom))
}

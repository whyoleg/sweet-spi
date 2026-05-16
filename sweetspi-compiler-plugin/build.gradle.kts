/*
 * Copyright (c) 2024-2026 Oleg Yukhnevich. Use of this source code is governed by the Apache 2.0 license.
 */

import com.vanniktech.maven.publish.*

plugins {
    id("sweetbuild.kotlin")
    id("sweetbuild.publication")
    alias(libs.plugins.kotlin.jvm)
}

description = "sweet-spi Compiler Plugin"

mavenPublishing {
    configure(KotlinJvm())
}

kotlin {
    compilerOptions {
        optIn.add("org.jetbrains.kotlin.ir.symbols.UnsafeDuringIrConstructionAPI")
    }
}

dependencies {
    compileOnly(kotlin("stdlib"))
    compileOnly(kotlin("compiler-embeddable"))
}

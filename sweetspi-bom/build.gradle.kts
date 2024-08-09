/*
 * Copyright (c) 2024 Oleg Yukhnevich. Use of this source code is governed by the Apache 2.0 license.
 */

import com.vanniktech.maven.publish.*
import sweetbuild.*

plugins {
    `java-platform`
    id("sweetbuild.publication")
}

description = "sweet-spi BOM"

dependencies {
    constraints {
        BOM_ARTIFACTS.forEach {
            api(project(":$it"))
        }
    }
}

mavenPublishing {
    configure(JavaPlatform())
}

/*
 * Copyright (c) 2024 Oleg Yukhnevich. Use of this source code is governed by the Apache 2.0 license.
 */

import com.vanniktech.maven.publish.VersionCatalog
import sweetbuild.*

plugins {
    `version-catalog`
    id("sweetbuild.publication")
}

description = "sweet-spi Gradle Version Catalog"

catalog {
    versionCatalog {
        // just a hint on a version used by the library
        version("kotlin", libs.versions.kotlin.asProvider().get())
        val sweetspiVersion = version("sweetspi", version.toString())
        (BOM_ARTIFACTS + "sweetspi-bom").forEach { name ->
            library(
                /* alias =    */ name.substringAfter("sweetspi-"),
                /* group =    */ "dev.whyoleg.sweetspi",
                /* artifact = */ name
            ).versionRef(sweetspiVersion)
        }
    }
}

mavenPublishing {
    configure(VersionCatalog())
}

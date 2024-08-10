/*
 * Copyright (c) 2024 Oleg Yukhnevich. Use of this source code is governed by the Apache 2.0 license.
 */

import org.jetbrains.kotlin.gradle.targets.js.nodejs.*
import org.jetbrains.kotlin.gradle.targets.js.npm.*

plugins {
    alias(libs.plugins.kotlin.dokka)

    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.multiplatform) apply false
    alias(libs.plugins.maven.publish) apply false
}

plugins.withType<NodeJsRootPlugin> {
    // ignore package lock
    extensions.configure<NpmExtension> {
        lockFileDirectory.set(layout.buildDirectory.dir("kotlin-js-store"))
        packageLockMismatchReport.set(LockFileMismatchReport.NONE)
    }
}

tasks.register<Copy>("mkdocsCopy") {
    into(rootDir.resolve("docs"))
    from("CHANGELOG.md")
    from("README.md")
}

tasks.register<Exec>("mkdocsBuild") {
    dependsOn(":mkdocsCopy")
    dependsOn(":sweetspi-runtime:dokkaHtml")
    dependsOn(":sweetspi-gradle-plugin:dokkaHtml")
    commandLine("mkdocs", "build", "--clean", "--strict")
}

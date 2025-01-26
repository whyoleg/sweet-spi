/*
 * Copyright (c) 2024-2025 Oleg Yukhnevich. Use of this source code is governed by the Apache 2.0 license.
 */

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

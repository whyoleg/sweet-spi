/*
 * Copyright (c) 2024 Oleg Yukhnevich. Use of this source code is governed by the Apache 2.0 license.
 */

@file:Suppress("HasPlatformType", "UnstableApiUsage")

import com.vanniktech.maven.publish.*
import sweetbuild.*

plugins {
    signing
    id("com.vanniktech.maven.publish.base")
}

mavenPublishing {
    publishToMavenCentral(SonatypeHost.S01)
    signAllPublications()

    pom {
        name.set(project.name)
        description.set(provider {
            checkNotNull(project.description) { "Project description isn't set for project: ${project.path}" }
        })
        url.set("https://github.com/whyoleg/sweet-spi")

        licenses {
            license {
                name.set("The Apache Software License, Version 2.0")
                url.set("https://www.apache.org/licenses/LICENSE-2.0.txt")
                distribution.set("repo")
            }
        }
        developers {
            developer {
                id.set("whyoleg")
                name.set("Oleg Yukhnevich")
                email.set("whyoleg@gmail.com")
            }
        }
        scm {
            connection.set("https://github.com/whyoleg/sweet-spi.git")
            developerConnection.set("https://github.com/whyoleg/sweet-spi.git")
            url.set("https://github.com/whyoleg/sweet-spi")
        }
    }
}

// we ignore the singing requirement because:
// * we should be able to run `publishToMavenLocal` without signing;
// * signing is necessary for Maven Central only, and it will anyway validate that the signature is present;
// * failure because of the absent signature will anyway fail only on CI during publishing release;
signing.isRequired = false

// Dev artifacts for tests publication - inspired by https://github.com/adamko-dev/dev-publish-plugin

val devArtifactsDirectory = layout.buildDirectory.dir("maven-dev-artifacts")

publishing.repositories.maven(devArtifactsDirectory) { name = "dev" }

configurations.consumable("devArtifacts") {
    devArtifactAttributes(objects)
    outgoing {
        artifact(devArtifactsDirectory) {
            builtBy(tasks.named("publishAllPublicationsToDevRepository"))
        }
    }
}

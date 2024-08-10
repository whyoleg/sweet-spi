/*
 * Copyright (c) 2024 Oleg Yukhnevich. Use of this source code is governed by the Apache 2.0 license.
 */

package sweetbuild

import org.gradle.api.artifacts.*
import org.gradle.api.attributes.*
import org.gradle.api.model.*
import org.gradle.kotlin.dsl.*

fun Configuration.devArtifactAttributes(objects: ObjectFactory) {
    attributes {
        attribute(Usage.USAGE_ATTRIBUTE, objects.named("dev-artifact"))
    }
}

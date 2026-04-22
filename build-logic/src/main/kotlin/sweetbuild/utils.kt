/*
 * Copyright (c) 2026 Oleg Yukhnevich. Use of this source code is governed by the Apache 2.0 license.
 */

package sweetbuild

import org.gradle.api.*
import org.gradle.api.provider.*

fun Project.booleanProperty(name: String, defaultValue: Boolean): Provider<Boolean> =
    providers.gradleProperty(name).map(String::toBoolean).orElse(defaultValue)

fun Project.stringProperty(name: String, defaultValue: String): Provider<String> =
    providers.gradleProperty(name).orElse(defaultValue)

/*
 * Copyright (c) 2024 Oleg Yukhnevich. Use of this source code is governed by the Apache 2.0 license.
 */

package dev.whyoleg.sweetspi.tests

import java.nio.file.*
import kotlin.io.path.*

class TestTemplate private constructor(path: String) {
    val templatePath: Path = templates.resolve(path)

    companion object {
        private val templates = Path("src/templates")

        val MULTIPLATFORM = TestTemplate("multiplatform")
        val JVM = TestTemplate("jvm")
        val ANDROID = TestTemplate("android")
    }
}

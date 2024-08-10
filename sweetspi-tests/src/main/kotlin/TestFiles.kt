/*
 * Copyright (c) 2024 Oleg Yukhnevich. Use of this source code is governed by the Apache 2.0 license.
 */

package dev.whyoleg.sweetspi.tests

import java.nio.file.*
import kotlin.io.path.*

const val BUILD_GRADLE_KTS = "build.gradle.kts"

class TestFiles(private val root: Path) {

    private fun Path.rewrite(block: (String) -> String): Unit = writeText(block(readText()))

    fun rewrite(path: String, block: (String) -> String) {
        root.resolve(path).rewrite(block)
    }

    fun append(path: String, block: () -> String) {
        rewrite(path) { it + "\n" + block() }
    }

    fun prepend(path: String, block: () -> String) {
        rewrite(path) { block() + "\n" + it }
    }

    fun file(path: String, block: () -> String) {
        root.resolve(path).createParentDirectories().writeText(block())
    }

    fun folder(path: String, block: TestFiles.() -> Unit) {
        TestFiles(root.resolve(path)).block()
    }
}

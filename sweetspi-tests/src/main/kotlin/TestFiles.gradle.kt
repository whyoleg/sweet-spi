/*
 * Copyright (c) 2024 Oleg Yukhnevich. Use of this source code is governed by the Apache 2.0 license.
 */

package dev.whyoleg.sweetspi.tests

fun TestFiles.buildGradleKts(
    plugins: List<String>,
    imports: List<String> = emptyList(),
) {
    file(BUILD_GRADLE_KTS) {
        """
        ${imports.joinToString("\n") { "import $it" }}
        plugins {
           ${plugins.joinToString("\n")}
        }
        """.trimIndent()
    }
}

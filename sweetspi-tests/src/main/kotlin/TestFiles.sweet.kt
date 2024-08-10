/*
 * Copyright (c) 2024 Oleg Yukhnevich. Use of this source code is governed by the Apache 2.0 license.
 */

package dev.whyoleg.sweetspi.tests

fun TestFiles.withSweetSpi() {
    append(BUILD_GRADLE_KTS) { "kotlin.withSweetSpi()" }
}

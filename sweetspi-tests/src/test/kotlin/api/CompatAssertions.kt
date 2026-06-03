/*
 * Copyright (c) 2026 Oleg Yukhnevich. Use of this source code is governed by the Apache 2.0 license.
 */

package dev.whyoleg.sweetspi.tests.api

import kotlin.io.path.*
import kotlin.test.*

// Reads the compiler-generated META-INF/services/<serviceId> for a built module + source set and
// asserts its entries. Generated resources live at:
//   <module>/build/generated/sweetspi/<sourceSet>/resources/META-INF/services/<serviceId>
// sourceSet is "main" for a kotlin-jvm module and "jvmMain" for the jvm target of a KMP module.
// Call after a jvm/kmp execution scenario has run (the build produced the files on disk).

context(project: TestProject)
private fun servicesPath(modulePath: String, sourceSet: String, serviceId: String) =
    project.projectDirectory.resolve(
        (if (modulePath.isEmpty()) "" else "$modulePath/") +
            "build/generated/sweetspi/$sourceSet/resources/META-INF/services/$serviceId"
    )

// File must exist. If [entries] is given, assert the exact set of (non-blank) lines; if [entryCount] is
// given, assert the number of entries. Pass [entries] for stable ids (e.g. a user class under FULL) and
// [entryCount] for generated wrapper ids whose names are deterministic but verbose.
context(project: TestProject)
fun assertServicesFile(
    modulePath: String = "",
    sourceSet: String = "main",
    serviceId: String,
    entries: Set<String>? = null,
    entryCount: Int? = null,
) {
    val path = servicesPath(modulePath, sourceSet, serviceId)
    assertTrue(path.exists(), "expected generated services file at $path")
    val actual = path.readText().lineSequence().filter { it.isNotBlank() }.toList()
    if (entries != null) assertEquals(entries, actual.toSet(), "entries in META-INF/services/$serviceId")
    if (entryCount != null) assertEquals(entryCount, actual.size, "entry count in META-INF/services/$serviceId")
}

context(project: TestProject)
fun assertNoServicesFile(modulePath: String = "", sourceSet: String = "main", serviceId: String) {
    val path = servicesPath(modulePath, sourceSet, serviceId)
    assertFalse(path.exists(), "expected NO generated services file at $path")
}

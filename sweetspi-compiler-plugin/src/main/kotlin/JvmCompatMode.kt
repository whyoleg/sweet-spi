/*
 * Copyright (c) 2026 Oleg Yukhnevich. Use of this source code is governed by the Apache 2.0 license.
 */

package dev.whyoleg.sweetspi.compiler

// The JVM `java.util.ServiceLoader` compatibility mode declared by `@Service(javaServiceLoaderCompatibilityMode)`.
// Names mirror the runtime `JavaServiceLoaderCompatibilityMode` enum entries so they can be matched by name
// when read from FIR/IR annotation arguments. Absent / unknown → NO_COMPATIBILITY (the runtime default).
enum class JvmCompatMode {
    NO_COMPATIBILITY,
    PROVIDE_COMPATIBILITY,
    FULL_COMPATIBILITY;

    companion object {
        fun fromEntryName(entryName: String?): JvmCompatMode =
            entries.firstOrNull { it.name == entryName } ?: NO_COMPATIBILITY
    }
}

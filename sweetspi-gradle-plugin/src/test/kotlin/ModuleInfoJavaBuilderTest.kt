/*
 * Copyright (c) 2026 Oleg Yukhnevich. Use of this source code is governed by the Apache 2.0 license.
 */
package dev.whyoleg.sweetspi.gradle.jpms

import kotlin.test.*

class ModuleInfoJavaBuilderTest {

    @Test
    fun `test requires module`() {
        assertEquals(
            expected = """
                       module test.module {
                           requires module.a;
                       }
                       """.trimIndent(),
            actual = moduleInfoJava("test.module") {
                requires("module.a")
            }
        )
    }

    @Test
    fun `test requiresTransitive module`() {
        assertEquals(
            expected = """
                       module test.module {
                           requires transitive module.b;
                       }
                       """.trimIndent(),
            actual = moduleInfoJava("test.module") {
                requiresTransitive("module.b")
            }
        )
    }

    @Test
    fun `test requiresStatic module`() {
        assertEquals(
            expected = """
                       module test.module {
                           requires static module.c;
                       }
                       """.trimIndent(),
            actual = moduleInfoJava("test.module") {
                requiresStatic("module.c")
            }
        )
    }

    @Test
    fun `test exports package`() {
        assertEquals(
            expected = """
                       module test.module {
                           exports pkg.a;
                       }
                       """.trimIndent(),
            actual = moduleInfoJava("test.module") {
                exports("pkg.a")
            }
        )
    }

    @Test
    fun `test exports package to single module`() {
        assertEquals(
            expected = """
                       module test.module {
                           exports pkg.b to module.x;
                       }
                       """.trimIndent(),
            actual = moduleInfoJava("test.module") {
                exports("pkg.b")
                exportsTo("pkg.b", "module.x")
            }
        )
    }

    @Test
    fun `test opens package`() {
        assertEquals(
            expected = """
                       module test.module {
                           opens pkg.a;
                       }
                       """.trimIndent(),
            actual = moduleInfoJava("test.module") {
                opens("pkg.a")
            }
        )
    }

    @Test
    fun `test opens package to modules`() {
        assertEquals(
            expected = """
                       module test.module {
                           opens pkg.a to module.x, module.y;
                       }
                       """.trimIndent(),
            actual = moduleInfoJava("test.module") {
                opens("pkg.a")
                opensTo("pkg.a", listOf("module.x", "module.y"))
            }
        )
    }

    @Test
    fun `test uses service`() {
        assertEquals(
            expected = """
                       module test.module {
                           uses service.a;
                       }
                       """.trimIndent(),
            actual = moduleInfoJava("test.module") {
                uses("service.a")
            }
        )
    }

    @Test
    fun `test provides service`() {
        assertEquals(
            expected = """
                       module test.module {
                           provides service.a with implementation.b;
                       }
                       """.trimIndent(),
            actual = moduleInfoJava("test.module") {
                provides("service.a", "implementation.b")
            }
        )
    }

    @Test
    fun `test throws when requiring a blank modulename`() {
        val builder = ModuleInfoJavaBuilder("test.module", isOpened = false)

        val exception = assertFailsWith<IllegalArgumentException> {
            builder.requires("")
        }

        assertEquals("Module name cannot be blank", exception.message)
    }

    @Test
    fun `test throws when exporting a blank package name`() {
        val builder = ModuleInfoJavaBuilder("test.module", isOpened = false)

        val exception = assertFailsWith<IllegalArgumentException> {
            builder.exports("")
        }

        assertEquals("Package name cannot be blank", exception.message)
    }

    @Test
    fun `test Open module with isOpened flag`() {
        assertEquals(
            expected = """
                       open module open.module {
                           requires module.a;
                       }
                       """.trimIndent(),
            actual = moduleInfoJava("open.module", isOpened = true) {
                requires("module.a")
            }
        )
    }
}
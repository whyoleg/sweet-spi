/*
 * Copyright (c) 2025 Oleg Yukhnevich. Use of this source code is governed by the Apache 2.0 license.
 */

package dev.whyoleg.sweetspi.tests.multiplatform

import dev.whyoleg.sweetspi.*
import kotlin.test.*

class TestServiceTest {
    @Test
    fun test() {
        val services = ServiceLoader.loadAll<TestService>()
        assertEquals(3, services.size)
        assertEquals(1, services.distinct().size)
        assertEquals("object", services.first().value())
    }

    @Test
    fun testMulti() {


        val first = ServiceLoader.loadAll<FirstService>()
        val second = ServiceLoader.loadAll<SecondService>()
        assertEquals(1, first.size)
        assertEquals(1, second.size)
        assertEquals(MultiServiceImpl, first.first())
        assertEquals(MultiServiceImpl, second.first())
    }
}

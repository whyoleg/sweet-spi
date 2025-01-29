/*
 * Copyright (c) 2025 Oleg Yukhnevich. Use of this source code is governed by the Apache 2.0 license.
 */

package dev.whyoleg.sweetspi.tests.multiplatform

import dev.whyoleg.sweetspi.*
import kotlin.test.*

@ServiceProvider
object InTestJvmServiceImpl : JvmService

@ServiceProvider
object InTestCommonServiceImpl : CommonService

class JvmServiceTest {
    @Test
    fun testJvm() {
        assertEquals(0, ServiceLoader.loadAll<JvmService>().size)
    }

    @Test
    fun testTest() {
        assertEquals(0, ServiceLoader.loadAll<TestService>().size)
    }

    @Test
    fun testCommon() {
        assertEquals(0, ServiceLoader.loadAll<CommonService>().size)
    }
}

/*
 * Copyright (c) 2025 Oleg Yukhnevich. Use of this source code is governed by the Apache 2.0 license.
 */

package dev.whyoleg.sweetspi.tests.multiplatform

import dev.whyoleg.sweetspi.*

@ServiceProvider
object TestServiceImpl : TestService {
    override fun value(): String = "object"
}

@ServiceProvider
fun testServiceImplFunction(): TestService = TestServiceImpl

@ServiceProvider
val testServiceImplProperty: TestService get() = TestServiceImpl

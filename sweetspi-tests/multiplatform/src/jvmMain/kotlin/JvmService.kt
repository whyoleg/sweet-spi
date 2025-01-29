/*
 * Copyright (c) 2025 Oleg Yukhnevich. Use of this source code is governed by the Apache 2.0 license.
 */

package dev.whyoleg.sweetspi.tests.multiplatform

import dev.whyoleg.sweetspi.*

@Service
interface JvmService

@ServiceProvider
object JvmServiceImpl : JvmService

@ServiceProvider
object CommonService2Impl : CommonService

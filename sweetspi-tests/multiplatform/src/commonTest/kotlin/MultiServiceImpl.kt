/*
 * Copyright (c) 2025 Oleg Yukhnevich. Use of this source code is governed by the Apache 2.0 license.
 */

package dev.whyoleg.sweetspi.tests.multiplatform

import dev.whyoleg.sweetspi.*

@Service
interface FirstService

@Service
interface SecondService

@ServiceProvider(
    services = [FirstService::class, SecondService::class],
//    FirstService::class,
//    SecondService::class
)
object MultiServiceImpl : SomeOtherService, SecondService


interface SomeOtherService : FirstService
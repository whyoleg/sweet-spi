/*
 * Copyright (c) 2024 Oleg Yukhnevich. Use of this source code is governed by the Apache 2.0 license.
 */

import sweetsettings.*

pluginManagement {
    includeBuild("build-logic")
    includeBuild("build-settings")
}

plugins {
    id("sweetsettings.default")
}

projects("sweet-spi") {
    // build-tools modules
    module("sweetspi-bom")
    module("sweetspi-version-catalog")

    // API
    module("sweetspi-runtime")
    module("sweetspi-processor")
    module("sweetspi-gradle-plugin")
    module("sweetspi-tests")
}

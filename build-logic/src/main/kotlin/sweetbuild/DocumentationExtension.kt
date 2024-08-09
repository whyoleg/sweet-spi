/*
 * Copyright (c) 2024 Oleg Yukhnevich. Use of this source code is governed by the Apache 2.0 license.
 */

package sweetbuild

import org.gradle.api.provider.*

abstract class DocumentationExtension {
    abstract val moduleName: Property<String>
    abstract val includes: Property<String>
}

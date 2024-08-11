/*
 * Copyright (c) 2024 Oleg Yukhnevich. Use of this source code is governed by the Apache 2.0 license.
 */

package dev.whyoleg.sweetspi.tests

import org.intellij.lang.annotations.*

const val MAIN = "main"
const val TEST = "test"
const val COMMON_MAIN = "commonMain"
const val COMMON_TEST = "commonTest"
const val JVM_MAIN = "jvmMain"
const val JVM_TEST = "jvmTest"
const val NATIVE_MAIN = "nativeMain"
const val NATIVE_TEST = "nativeTest"

fun TestFiles.jvmTarget() {
    append(BUILD_GRADLE_KTS) {
        """
        kotlin {
            jvm()
        }    
        """.trimIndent()
    }
}

fun TestFiles.webTargets() {
    append(BUILD_GRADLE_KTS) {
        """
        kotlin {
            js { nodejs(); browser() }
            wasmJs { nodejs(); browser() }
            wasmWasi { nodejs() }
        }
        """.trimIndent()
    }
}

fun TestFiles.nativeTargets() {
    append(BUILD_GRADLE_KTS) {
        // apple-related targets can be used only on macOS
        // we can't build runtime for them in this case
        if (System.getProperty("os.name")?.startsWith("Mac") == true) {
            """
            kotlin {
                iosArm64(); iosX64(); iosSimulatorArm64()
                watchosX64(); watchosArm32(); watchosArm64(); watchosSimulatorArm64(); watchosDeviceArm64()
                tvosX64(); tvosArm64(); tvosSimulatorArm64()
                macosX64(); macosArm64()
                
                linuxX64(); linuxArm64()
                mingwX64()
                androidNativeX64(); androidNativeX86(); androidNativeArm64(); androidNativeArm32()
            }
            """.trimIndent()
        } else {
            """
            kotlin {
                linuxX64(); linuxArm64()
                mingwX64()
                androidNativeX64(); androidNativeX86(); androidNativeArm64(); androidNativeArm32()
            }
            """.trimIndent()
        }
    }
}

fun TestFiles.allTargets() {
    jvmTarget()
    webTargets()
    nativeTargets()
}

fun TestFiles.kotlinJvmTest() {
    append(BUILD_GRADLE_KTS) {
        """
        dependencies {  
            implementation(kotlin("test"))
        }    
        """.trimIndent()
    }
}

fun TestFiles.kotlinTest(sourceSet: String) {
    append(BUILD_GRADLE_KTS) {
        """
        kotlin { 
            sourceSets.${sourceSet}.dependencies { 
                implementation(kotlin("test"))
            }
        }    
        """.trimIndent()
    }
}

fun TestFiles.kotlinSourceFile(
    sourceSet: String,
    path: String,
    packageName: String = "sweettests.multiplatform",
    imports: List<String> = emptyList(),
    @Language("kotlin")
    code: String,
) {
    file("src/$sourceSet/kotlin/$path") {
        buildString {
            append("package $packageName\n")
            append("import dev.whyoleg.sweetspi.*")
            imports.joinTo(buffer = this, separator = "\n", postfix = "\n") { "import $it" }
            append(code)
        }
    }
}

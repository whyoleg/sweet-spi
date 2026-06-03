/*
 * Copyright (c) 2026 Oleg Yukhnevich. Use of this source code is governed by the Apache 2.0 license.
 */

package dev.whyoleg.sweetspi.tests

import dev.whyoleg.sweetspi.tests.api.*
import kotlin.test.*

// Single-module execution: verifies FIR-generated $ServiceProvider / $ServiceProviderRegistry are
// produced and discovered correctly regardless of WHICH source set the @Service / @Provides live in —
// commonMain, the `nonJvm` intermediate, or the `jvmAndNative` intermediate (the registry-vs-METAINF
// divergence). All on the canonical matrix (see multiplatformExecutionBuildFile).
//
// Host note (macOS/arm64): jvm + macosArm64 + wasm node tests run; linuxX64 is compile-only.
class SourceSetPlacementTest : TestProjectBasedTest() {

    // @Service + @Provides + consumer all in commonMain — discovered on every target (JVM META-INF and
    // klib registry alike).
    @Test
    fun commonMain() = context(project) {
        bootstrapKotlinMultiplatformExecutionProject()
        file("src/commonMain/kotlin/Service.kt").kotlin("sweettests", GreetingFixture.service)
        file("src/commonMain/kotlin/Provider.kt").kotlin("sweettests", GreetingFixture.provider)
        file("src/commonTest/kotlin/GreetingTest.kt").kotlin("sweettests", GreetingFixture.consumerTest)

        gradle(
            ":jvmTest", ":macosArm64Test", ":wasmJsNodeTest", ":wasmWasiNodeTest",
            ":compileTestKotlinLinuxX64"
        ) {
            assertTrue(task(":jvmTest")!!.outcome.isPositive)
            assertTrue(task(":macosArm64Test")!!.outcome.isPositive)
            assertTrue(task(":wasmJsNodeTest")!!.outcome.isPositive)
            assertTrue(task(":wasmWasiNodeTest")!!.outcome.isPositive)
        }
    }

    // @Service in commonMain (visible everywhere), @Provides in the `nonJvm` intermediate. Exercises a
    // provider in a narrower source set resolving the commonMain service's FIR nested types. jvm compiles
    // the service but has no provider, so it is not asserted.
    @Test
    fun providerInNonJvm() = context(project) {
        bootstrapKotlinMultiplatformExecutionProject()
        file("src/commonMain/kotlin/Service.kt").kotlin("sweettests", GreetingFixture.service)
        file("src/nonJvmMain/kotlin/Provider.kt").kotlin("sweettests", GreetingFixture.provider)
        file("src/nonJvmTest/kotlin/GreetingTest.kt").kotlin("sweettests", GreetingFixture.consumerTest)

        gradle(
            ":compileKotlinJvm", ":macosArm64Test", ":wasmJsNodeTest", ":wasmWasiNodeTest",
            ":compileTestKotlinLinuxX64"
        ) {
            assertTrue(task(":macosArm64Test")!!.outcome.isPositive)
            assertTrue(task(":wasmJsNodeTest")!!.outcome.isPositive)
            assertTrue(task(":wasmWasiNodeTest")!!.outcome.isPositive)
        }
    }

    // @Service itself declared in the `nonJvm` intermediate (never seen by jvm). Confirms FIR generation
    // fires for a service declared in an intermediate source set.
    @Test
    fun serviceInNonJvm() = context(project) {
        bootstrapKotlinMultiplatformExecutionProject()
        file("src/nonJvmMain/kotlin/Service.kt").kotlin("sweettests", GreetingFixture.service)
        file("src/nonJvmMain/kotlin/Provider.kt").kotlin("sweettests", GreetingFixture.provider)
        file("src/nonJvmTest/kotlin/GreetingTest.kt").kotlin("sweettests", GreetingFixture.consumerTest)

        gradle(":macosArm64Test", ":wasmJsNodeTest", ":wasmWasiNodeTest", ":compileTestKotlinLinuxX64") {
            assertTrue(task(":macosArm64Test")!!.outcome.isPositive)
            assertTrue(task(":wasmJsNodeTest")!!.outcome.isPositive)
            assertTrue(task(":wasmWasiNodeTest")!!.outcome.isPositive)
        }
    }

    // The divergence — @Service + @Provides in the `jvmAndNative` intermediate. The one declaration is
    // discovered via JVM META-INF on jvm and via the FIR-generated registry on native, through a shared
    // source set whose metadata compilation generates the registry while the jvm leaf does not.
    @Test
    fun jvmAndNativeIntermediate() = context(project) {
        bootstrapKotlinMultiplatformExecutionProject()
        file("src/jvmAndNativeMain/kotlin/Service.kt").kotlin("sweettests", GreetingFixture.service)
        file("src/jvmAndNativeMain/kotlin/Provider.kt").kotlin("sweettests", GreetingFixture.provider)
        file("src/jvmAndNativeTest/kotlin/GreetingTest.kt").kotlin("sweettests", GreetingFixture.consumerTest)

        gradle(":jvmTest", ":macosArm64Test", ":compileTestKotlinLinuxX64") {
            assertTrue(task(":jvmTest")!!.outcome.isPositive)
            assertTrue(task(":macosArm64Test")!!.outcome.isPositive)
        }
    }
}

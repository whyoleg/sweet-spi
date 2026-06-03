/*
 * Copyright (c) 2026 Oleg Yukhnevich. Use of this source code is governed by the Apache 2.0 license.
 */

package dev.whyoleg.sweetspi.tests

import dev.whyoleg.sweetspi.tests.api.*
import kotlin.io.path.*
import kotlin.test.*

// Exercises FIR-generated $ServiceProvider / $ServiceProviderRegistry across multiplatform source-set
// hierarchies (intermediate source sets, multiple natives, js, wasmJs), where per-compilation divergence
// (registry on non-JVM, META-INF on JVM) is most likely to break. See M1 plan / roadmap G8.
//
// Host note (macOS/arm64): macosArm64 + js/wasm node tests execute; linuxX64 only compiles (its test task
// is skipped), so it is run compile-only via :compileTestKotlinLinuxX64 for extra per-target coverage.
class MultiplatformHierarchyTest : TestProjectBasedTest() {

    private val service = """
        import dev.whyoleg.sweetspi.*
        @Service interface GreetingService { fun greet(): String }
    """.trimIndent()

    private val provider = """
        import dev.whyoleg.sweetspi.*
        @Provides(GreetingService::class) object EnglishGreeting : GreetingService {
            override fun greet(): String = "hello"
        }
    """.trimIndent()

    private val consumerTest = """
        import dev.whyoleg.sweetspi.*
        import kotlin.test.*
        class GreetingTest {
            @Test
            fun test() {
                val providers = ServiceLoader.load<GreetingService>().toList()
                assertEquals(1, providers.size)
                assertEquals("hello", providers.single().value.greet())
            }
        }
    """.trimIndent()

    // S1: broad single-module — @Service + @Provides in commonMain, consumed in commonTest, across
    // jvm + 2 natives + js + wasmJs. The same service is discovered via JVM META-INF and native registry.
    @Test
    fun commonMainBroad() = context(project) {
        bootstrapKotlinMultiplatformProject()
        file("build.gradle.kts").appendText(multiplatformTargets())

        file("src/commonMain/kotlin/Service.kt").kotlin("sweettests", service)
        file("src/commonMain/kotlin/Provider.kt").kotlin("sweettests", provider)
        file("src/commonTest/kotlin/GreetingTest.kt").kotlin("sweettests", consumerTest)

        gradle(":jvmTest", ":macosArm64Test", ":jsNodeTest", ":wasmJsNodeTest", ":compileTestKotlinLinuxX64") {
            assertTrue(task(":jvmTest")!!.outcome.isPositive)
            assertTrue(task(":macosArm64Test")!!.outcome.isPositive)
            assertTrue(task(":jsNodeTest")!!.outcome.isPositive)
            assertTrue(task(":wasmJsNodeTest")!!.outcome.isPositive)
        }
    }

    // S2: @Service in commonMain (visible to all), @Provides in the `nonJvm` intermediate. Exercises a
    // provider in a narrower source set resolving the commonMain service's FIR nested types, and the
    // nonJvm metadata compilation generating the registry. jvm compiles the service but has no provider.
    @Test
    fun providerInNonJvm() = context(project) {
        bootstrapKotlinMultiplatformProject()
        file("build.gradle.kts").appendText(multiplatformTargets(extra = nonJvmSourceSets()))

        file("src/commonMain/kotlin/Service.kt").kotlin("sweettests", service)
        file("src/nonJvm/kotlin/Provider.kt").kotlin("sweettests", provider)
        file("src/nonJvmTest/kotlin/GreetingTest.kt").kotlin("sweettests", consumerTest)

        gradle(":compileKotlinJvm", ":macosArm64Test", ":jsNodeTest", ":wasmJsNodeTest", ":compileTestKotlinLinuxX64") {
            assertTrue(task(":macosArm64Test")!!.outcome.isPositive)
            assertTrue(task(":jsNodeTest")!!.outcome.isPositive)
            assertTrue(task(":wasmJsNodeTest")!!.outcome.isPositive)
        }
    }

    // S2b: @Service itself declared in the `nonJvm` intermediate (never seen by jvm), @Provides + consumer
    // alongside it. Confirms FIR generation fires for a service declared in an intermediate source set.
    @Test
    fun serviceInNonJvm() = context(project) {
        bootstrapKotlinMultiplatformProject()
        file("build.gradle.kts").appendText(multiplatformTargets(extra = nonJvmSourceSets()))

        file("src/nonJvm/kotlin/Service.kt").kotlin("sweettests", service)
        file("src/nonJvm/kotlin/Provider.kt").kotlin("sweettests", provider)
        file("src/nonJvmTest/kotlin/GreetingTest.kt").kotlin("sweettests", consumerTest)

        gradle(":macosArm64Test", ":jsNodeTest", ":wasmJsNodeTest", ":compileTestKotlinLinuxX64") {
            assertTrue(task(":macosArm64Test")!!.outcome.isPositive)
            assertTrue(task(":jsNodeTest")!!.outcome.isPositive)
            assertTrue(task(":wasmJsNodeTest")!!.outcome.isPositive)
        }
    }

    // S3: the divergence — @Service + @Provides in the `jvmAndNative` intermediate. The one declaration is
    // discovered via JVM META-INF on jvm and via the FIR-generated registry on native, through a shared
    // source set whose metadata compilation generates the registry while the jvm leaf does not.
    @Test
    fun jvmAndNativeIntermediate() = context(project) {
        bootstrapKotlinMultiplatformProject()
        file("build.gradle.kts").appendText(
            multiplatformTargets(js = false, wasmJs = false, extra = jvmAndNativeSourceSets())
        )

        file("src/jvmAndNative/kotlin/Service.kt").kotlin("sweettests", service)
        file("src/jvmAndNative/kotlin/Provider.kt").kotlin("sweettests", provider)
        file("src/jvmAndNativeTest/kotlin/GreetingTest.kt").kotlin("sweettests", consumerTest)

        gradle(":jvmTest", ":macosArm64Test", ":compileTestKotlinLinuxX64") {
            assertTrue(task(":jvmTest")!!.outcome.isPositive)
            assertTrue(task(":macosArm64Test")!!.outcome.isPositive)
        }
    }

    // S4: multi-module across a hierarchy — :service exposes @Service in commonMain; :provider declares
    // @Provides in its `nonJvm` intermediate (depending on :service); :consumer loads it from `nonJvmTest`
    // (depending only on :provider). Validates cross-module resolution of the FIR-generated nested types
    // when the provider lives in an intermediate source set.
    //
    // Asserts native + wasmJs (cross-module works there). js is compiled but NOT run — see
    // multiModuleProviderInNonJvm_js below for the js-only known limitation.
    @Test
    fun multiModuleProviderInNonJvm() = context(project) {
        setupMultiModuleNonJvm()

        gradle(
            ":consumer:macosArm64Test", ":consumer:wasmJsNodeTest",
            ":consumer:compileTestKotlinJs", ":consumer:compileTestKotlinLinuxX64"
        ) {
            assertTrue(task(":consumer:macosArm64Test")!!.outcome.isPositive)
            assertTrue(task(":consumer:wasmJsNodeTest")!!.outcome.isPositive)
        }
    }

    // KNOWN-LIMITATION (M6, js only — wasmJs works): cross-module SPI does not work on js. A @Provides in a
    // separately-linked module registers via an @EagerInitialization property, but on js that module's
    // top-level initializers run only when the module is actually loaded — and the consumer reaches the
    // provider module only reflectively (associated-object registry), so it is never loaded and the
    // registry stays empty (load() returns 0). Works on jvm (META-INF), native (whole-program link), and
    // wasmJs. Fixing js is tracked in the roadmap M6 spike; un-ignore when it lands.
    @Ignore
    @Test
    fun multiModuleProviderInNonJvm_js() = context(project) {
        setupMultiModuleNonJvm()

        gradle(":consumer:jsNodeTest") {
            assertTrue(task(":consumer:jsNodeTest")!!.outcome.isPositive)
        }
    }

    context(project: TestProject)
    private fun setupMultiModuleNonJvm() {
        bootstrapMultiModuleProject(listOf("service", "provider", "consumer"))

        multiplatformBuildFile("service").appendText(multiplatformTargets())
        file("service/src/commonMain/kotlin/Service.kt").kotlin("sweettests", service)

        multiplatformBuildFile("provider").appendText(
            multiplatformTargets(extra = nonJvmSourceSets()) +
                """
                kotlin { sourceSets.commonMain.dependencies { api(project(":service")) } }
                """.trimIndent()
        )
        file("provider/src/nonJvm/kotlin/Provider.kt").kotlin("sweettests", provider)

        multiplatformBuildFile("consumer").appendText(
            multiplatformTargets(extra = nonJvmSourceSets()) +
                """
                kotlin { sourceSets.commonMain.dependencies { implementation(project(":provider")) } }
                """.trimIndent()
        )
        file("consumer/src/nonJvmTest/kotlin/GreetingTest.kt").kotlin("sweettests", consumerTest)
    }
}

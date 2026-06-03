/*
 * Copyright (c) 2026 Oleg Yukhnevich. Use of this source code is governed by the Apache 2.0 license.
 */

package dev.whyoleg.sweetspi.tests

import dev.whyoleg.sweetspi.tests.api.*
import kotlin.io.path.*
import kotlin.test.*

// Cross-module execution (the "library" promise): @Service in :service, @Provides in :provider,
// ServiceLoader.load in :consumer. Validates that the FIR-generated $ServiceProvider / registry are part
// of the consumable ABI so a dependent module resolves them across module boundaries. Covers jvm and the
// canonical kmp matrix, including a provider declared in an intermediate (`nonJvm`) source set.
class CrossModuleTest : TestProjectBasedTest() {

    // JVM-only multi-module: :consumer depends on :provider, which re-exposes :service via `api(...)`.
    @Test
    fun jvm() = context(project) {
        bootstrapMultiModuleProject(listOf("service", "provider", "consumer"))

        jvmBuildFile("service")
        file("service/src/main/kotlin/Service.kt").kotlin("sweettests", GreetingFixture.service)

        jvmBuildFile("provider").appendText("dependencies { api(project(\":service\")) }\n")
        file("provider/src/main/kotlin/Provider.kt").kotlin("sweettests", GreetingFixture.provider)

        jvmBuildFile("consumer").appendText("dependencies { implementation(project(\":provider\")) }\n")
        file("consumer/src/test/kotlin/GreetingTest.kt").kotlin("sweettests", GreetingFixture.consumerTest)

        gradle(":consumer:test") {
            assertTrue(task(":consumer:test")!!.outcome.isPositive)
        }
    }

    // Multiplatform multi-module on the canonical matrix; @Service / @Provides in commonMain.
    @Test
    fun multiplatform() = context(project) {
        bootstrapMultiModuleProject(listOf("service", "provider", "consumer"))

        multiplatformExecutionBuildFile("service")
        file("service/src/commonMain/kotlin/Service.kt").kotlin("sweettests", GreetingFixture.service)

        multiplatformExecutionBuildFile("provider").appendText(commonMainDependency("api", ":service"))
        file("provider/src/commonMain/kotlin/Provider.kt").kotlin("sweettests", GreetingFixture.provider)

        multiplatformExecutionBuildFile("consumer").appendText(commonMainDependency("implementation", ":provider"))
        file("consumer/src/commonTest/kotlin/GreetingTest.kt").kotlin("sweettests", GreetingFixture.consumerTest)

        gradle(
            ":consumer:jvmTest", ":consumer:macosArm64Test", ":consumer:wasmJsNodeTest",
            ":consumer:wasmWasiNodeTest", ":consumer:compileTestKotlinLinuxX64"
        ) {
            assertTrue(task(":consumer:jvmTest")!!.outcome.isPositive)
            assertTrue(task(":consumer:macosArm64Test")!!.outcome.isPositive)
            assertTrue(task(":consumer:wasmJsNodeTest")!!.outcome.isPositive)
            assertTrue(task(":consumer:wasmWasiNodeTest")!!.outcome.isPositive)
        }
    }

    // Provider declared in the :provider module's `nonJvm` intermediate (not commonMain). Asserts native +
    // wasmJs; js is a known limitation (see providerInNonJvm_js).
    @Test
    fun providerInNonJvm() = context(project) {
        setupMultiModuleNonJvm()

        gradle(":consumer:macosArm64Test", ":consumer:wasmJsNodeTest", ":consumer:compileTestKotlinLinuxX64") {
            assertTrue(task(":consumer:macosArm64Test")!!.outcome.isPositive)
            assertTrue(task(":consumer:wasmJsNodeTest")!!.outcome.isPositive)
        }
    }

    // KNOWN-LIMITATION (js only — wasmJs works): cross-module SPI does not work on js. A @Provides in a
    // separately-linked module registers via an @EagerInitialization property, but on js that module's
    // top-level initializers run only when the module is actually loaded — and the consumer reaches the
    // provider module only reflectively (associated-object registry), so it is never loaded and the
    // registry stays empty (load() returns 0). Works on jvm (META-INF), native, and wasmJs. Fixing js is
    // tracked in the roadmap M6 spike; un-ignore when it lands.
    @Ignore
    @Test
    fun providerInNonJvm_js() = context(project) {
        setupMultiModuleNonJvm(withJs = true)

        gradle(":consumer:jsNodeTest") {
            assertTrue(task(":consumer:jsNodeTest")!!.outcome.isPositive)
        }
    }

    // service in commonMain (:service); provider in the :provider module's `nonJvmMain` intermediate;
    // consumer loads from :consumer's `nonJvmTest`. `withJs` adds the js target to all modules (only the
    // @Ignore'd js tracker needs it — js is otherwise absent from the canonical matrix).
    context(project: TestProject)
    private fun setupMultiModuleNonJvm(withJs: Boolean = false) {
        bootstrapMultiModuleProject(listOf("service", "provider", "consumer"))
        val js = if (withJs) "kotlin { js { nodejs() } }\n" else ""

        multiplatformExecutionBuildFile("service").appendText(js)
        file("service/src/commonMain/kotlin/Service.kt").kotlin("sweettests", GreetingFixture.service)

        multiplatformExecutionBuildFile("provider").appendText(commonMainDependency("api", ":service") + js)
        file("provider/src/nonJvmMain/kotlin/Provider.kt").kotlin("sweettests", GreetingFixture.provider)

        multiplatformExecutionBuildFile("consumer").appendText(commonMainDependency("implementation", ":provider") + js)
        file("consumer/src/nonJvmTest/kotlin/GreetingTest.kt").kotlin("sweettests", GreetingFixture.consumerTest)
    }

    private fun commonMainDependency(configuration: String, project: String): String =
        "kotlin { sourceSets.commonMain.dependencies { $configuration(project(\"$project\")) } }\n"
}

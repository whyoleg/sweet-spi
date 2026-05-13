# sweet-spi

> [!CAUTION]
> The project is currently not actively maintained, and is in the process of rewriting its logic from KSP to Kotlin Compiler Plugin.
>
> See https://github.com/whyoleg/sweet-spi/issues/21 for current status and future plans.

Simple SPI (Service Provider Interface) for Kotlin Multiplatform (equivalent of JVM's Service Loader)

```kotlin
// module: A
@Service
interface SimpleService {
    fun saySomethingSweet()
}

// module: A, B or even in a library!
@ServiceProvider
object SimpleServiceImpl : SimpleService {
    override fun saySomethingSweet() {
        println("Kotlin is Awesome")
    }
}

// module: A, B, C or may be not even in your codebase...
fun main() {
    ServiceLoader.load<SimpleService>().forEach { service ->
        service.saySomethingSweet()
    }
}
```

## Overview

sweet-spi consists of three parts:

* multiplatform runtime with a tiny public API and all 24 supported targets
* KSP processor which generates a bit of glue code for each target
* Gradle Plugin which simplifies setup of KSP and runtime

Documentation can be found here, or on the website:

* [Project website](https://whyoleg.github.io/sweet-spi/)
* [Runtime API reference](https://whyoleg.github.io/sweet-spi/runtime-api/)
* [Gradle Plugin API reference](https://whyoleg.github.io/sweet-spi/gradle-plugin-api/)

## Features

- Easy-to-use SPI with just two annotations and two functions
- No additional setup is necessary for consumers, except for depending on your library, of course
- Automatic loading of service providers from any module or library which is available at runtime
- Automatic discovery of services based on the declaration type
- Support for providing services via objects, properties, and no-arg functions
- Support tor providing multiple services via one declaration
- Ability to explicitly specify which services should be provided by the specific service provider
- Strict identification about which interfaces could be loaded
- Supports both kotlin-jvm and kotlin-multiplatform plugins (android plugins support will be added later, not tested now, so may even work)

## Using in your projects

Compatible with Kotlin 2.0.0+ and KSP 1.0.24+ (tested up to including Kotlin 2.2.20-RC and KSP 2.0.2).
Using other Kotlin/KSP versions should still work but is not tested.

```kotlin
// In your build.gradle.kts file:

// ATTENTION: this import is REQUIRED
import dev.whyoleg.sweetspi.gradle.*

plugins {
    // apply kotlin-jvm or kotlin-multiplatform plugin 
    kotlin("multiplatform") version "2.0.0"
    // apply KSP, don't worry, Gradle Plugin will warn if you've forgotten 
    id("com.google.devtools.ksp") version "2.0.0-1.0.24"
    // finally, apply `sweetspi` Gradle Plugin 
    id("dev.whyoleg.sweetspi") version "0.1.3"
}

kotlin {
    // just one line and we are good to go
    withSweetSpi()

    // declare Kotlin targets
    jvm()
    wasmJs { browser() }
    iosArm64()
    iosX64()
    iosSimulatorArm64()
    // and any other target
}
```

sweet-spi Gradle plugin will automatically add runtime and KSP processor dependencies to appropriate configurations:

* runtime will be added to `implementation` scope of `main` source set tree
    * if you need to expose sweet-spi to your consumers, just add `api(sweetSpiRuntime())` to `dependencies` block of the appropriate source
      set
    * it's also possible to configure sweet-spi only for specific target or even compilation using the same named extension function
      `withsweetSpi` but on `KotlinTarget` or `KotlinCompilation`
* KSP processor will be added to relevant configuration in all the same places where `withSweetSpi` is called
    * by default, KSP processor is not applied for `test` source set tree.
      For this you could manually add KSP processor there, or use `compilationFilter` parameter of `withSweetSpi` function
    * Gradle Plugin performs several checks to be sure that KSP is configured correctly.
      If for some reason it produces false-positive results, it's possible to suppress it by adding
      `dev.whyoleg.sweetspi.suppressGradleKspConfigurationChecker=true` to your `gradle.properties` file

## Manual setup without sweet-spi Gradle Plugin

As stated above, Gradle Plugin is contains just checkers and a small amount of helper functions, this means, that it's possible to use
sweet-spi without it:

```kotlin
// In your build.gradle.kts file:

plugins {
    kotlin("multiplatform") version "2.0.0"
    id("com.google.devtools.ksp") version "2.0.0-1.0.24"
}

kotlin {
    // declare Kotlin targets
    jvm()
    wasmJs { browser() }
    iosArm64()
    iosX64()
    iosSimulatorArm64()
    // and any other target

    sourceSets {
        commonMain.dependencies {
            // add runtime
            implementation("dev.whyoleg.sweetspi:sweetspi-runtime:0.1.3")
        }
    }
}

dependencies {
    // Note: `ksp` configuration should be used only for jvm projects
    // it's deprecated and will be removed in KSP 2.0
    // https://kotlinlang.org/docs/ksp-multiplatform.html#avoid-the-ksp-configuration-on-ksp-1-0-1
    ksp("dev.whyoleg.sweetspi:sweetspi-processor:0.1.3")

    // the correct way will be to apply to each target individually, 
    // Note: no need to add it to the `common` compilations, as it'd do nothing there
    add("kspJvm", "dev.whyoleg.sweetspi:sweetspi-processor:0.1.3")
    // if needed, `test` source sets support
    add("kspJvmTest", "dev.whyoleg.sweetspi:sweetspi-processor:0.1.3")
    // and for each other Kotlin target...
    add("kspLinuxX64Test", "dev.whyoleg.sweetspi:sweetspi-processor:0.1.3")
}
```

## Implementation details

* on JVM standard [ServiceLoader](https://docs.oracle.com/javase/8/docs/api/java/util/ServiceLoader.html) API is used
* on other targets [EagerInitialization](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin.native/-eager-initialization/) annotation is
  used.
  Note: this API is experimental/deprecated for some amount of time (AFAIK it was `deprecated` from the beginning). But, it works!

## Bugs and Feedback

Of course there could be some bugs, feel free to report them, it would really mean a lot to me!
For bugs, questions, and discussions, please use the [GitHub Issues](https://github.com/whyoleg/sweet-spi/issues)

## License

This project is licensed under the Apache 2.0 license. See the [LICENSE](LICENSE) file for details.

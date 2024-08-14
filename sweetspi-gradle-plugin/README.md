# Module sweetspi-gradle-plugin

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

## Using in your projects

Compatible with Kotlin 2.0.0 and 2.0.10 and KSP 2.0.0-1.0.24+.
KSP2 is not supported because of [KSP#1823](https://github.com/google/ksp/issues/1823)
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
    id("dev.whyoleg.sweetspi") version "0.1.1"
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

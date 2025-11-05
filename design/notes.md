# Notes

## Questions

- [ ] Why ServiceLoader?
- [ ] Why SL in KMP
- [ ] Android
- [ ] Alternatives
- [ ] Other languages
- [ ] What about DI/ServiceLocator
- [ ] Alternative - generate code or require initializers at runtime
- [ ] compile time vs runtime
- [ ] 

## Use cases

- [ ] cryptography-kotlin - pluggable PLATFORM providers (as with JCA)
- [ ] ktor - automatic PLATFORM dependent client engine resolve
- [ ] Logging - auto formatter/backends resolve
- [ ] Configuration - auto resolve of supported file extensions
- [ ] SQL drivers - platform specific

Plugin/component naming feels better
Check idea/kotlin for ServiceLoader usage

Combine APIs:

- ServiceLocator
- ServiceLoader
- May be annotations access?
- How to support module-info?
- Jvm compat layer
- ServiceBootstrap
- Investigate multiple services bootstrap
- Integrate bootstrap with loader

TODO:

* How to do all those things explicitly without ServiceLoader like API?
* Check how we can help with https://blog.insert-koin.io/koin-framework-2025-roadmap-from-4-0-to-future-milestones-68b0558e56a9
* TESTS: what to do in tests?
* Check custom file formats support based on class name?

When there are multiple such systems, the order does matter!

LoggingSystem.initialize(
Slf4JLogger
CustomLogger(parameters)

expect val engine: HttpClientEngine

actual val engine: HttpClientEngine = CIO
actual val engine: HttpClientEngine = Darwin
actual val engine: HttpClientEngine = JS

ConfigurationSystem.Formats.initialize(
YamlSupport,
JsonSupport,
PropertiesSupport,
XmlSupport,
…
)

https://stackoverflow.com/questions/48090929/confused-about-java-9-serviceloaderload-method-and-the-way-how-to-provide-a-se

https://developer.android.com/topic/libraries/app-startup

* SPI (in Java) is similar to Service Locator pattern
* Other languages prefer explicit initialization, e.g bootstrapping code which should be called only in application:
    * Swift: LoggingSystem.boostrap(…)
    * Rust: log.set_logger(…)
* In most cases, just SPI could be not enough, as provided service might need additional configuration and in case of SPI it will configured
  indirectly
* In KMP bootstrapping could be not convenient to use if provided services are coming from platform specific artifacts (ktor and CK) but
  should work fine for cases when provider is in common (logit, anyconf)
* EagerInitialization in klib backends looks like more versatile approach which could support not only SPI like use-cases
* Bootstrapping in application could become cumbersome if there will be a lot of such projects, but may be it could be not an issue, as DI’s
  could have integration with it
* The most questionable thing is KMP platform specific initialization
* SPI - can provide multiple services (e.g file extensions support for anyconf)
* Bootstrapping(BS) - mostly to provide some single default implementation, but can be explicitly extended for multiple instances
* SPI or EagerInitialization inside libraries can be not very intuitive in regards to “implicitness” while Kotlin strive for “explicitness”
    * Additionally, in case of SPI it’s not possible to opt-out of loading some of the providers:
        * e.g if library providing service which we don’t want to use
    * With BS users explicitly say what they need
* BS - may be harder for users
* Coroutines use-case: initialization of MainDispatcher which could be different per environment:
    * Android
    * Swing
    * JavaFX
    * ???
* SPI - works for simple cases when configuration for provided service is not needed, e.g. when services are stateless
* In SPI - service can be selected based on it’s properties
* SPI allows to declare service in API module and provide implementation in other modules without users knowing about it
* What to do in tests?
* Dlopen?
* SPI in Java helps with “plugins” architecture

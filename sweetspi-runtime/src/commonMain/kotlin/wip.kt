/*
 * Copyright (c) 2026 Oleg Yukhnevich. Use of this source code is governed by the Apache 2.0 license.
 */

package dev.whyoleg.sweetspi

// NOTE: we can't just add `loadAsync` in addition to `load` because only part of it will work depending on constructed pattern.
// e.g. on JS if we would like to have `import("")` support, it will work only via `loadAsync`.
// so this really means, that to support async loading, we need to provide a separate interface for async loading.
// overall, if the prefetching is performed before constructing `ServiceLoader` - it's really aligned with how other platforms work here
// on JVM jars should be already downloaded
// on native, native libs should be already downloaded
// so for real `async` plugin loading via `import` we need another approach


//// meta-annotation to add additional information to `@Provides` annotated implementations
//@MustBeDocumented
//@Target(AnnotationTarget.ANNOTATION_CLASS)
//public annotation class ServiceInfo

//
//// KClass on JVM, just class on other platforms???
//public expect interface ServiceProviderType<@Suppress("unused") T : Any> {
//    public val simpleName: String?
//    public val qualifiedName: String?
//
//    // TODO: on JVM it will also include other annotations - is it fine? - I think so.
//    public val annotations: List</*@ServiceInfo*/ Annotation>
//
//    // actuality for JVM
//    override fun equals(other: Any?): Boolean
//    override fun hashCode(): Int
//}


// test usage
//@Service
//public interface X {
//    public interface `$ServiceProvider` : ServiceProvider<X>
//}
//
//@Service
//public interface Y {
//    public interface `$ServiceProvider` : ServiceProvider<Y>
//}

// no receiver, no context parameters
// @Provides(X::class) object XY_Impl : X
// @Provides(X::class) fun provide(): X = XY_Impl
// @Provides(X::class) fun provide(x: String = ""): X = XY_Impl // ignore - check in checker
// @Provides(X::class) val lazyDefault: X by lazy { XY_Impl }
// generates
// internal     class `provide$dev_whyoleg_sweetspi_X$ServiceProvider`    : X.`$ServiceProvider`
// internal     class `lazyDefault$dev_whyoleg_sweetspi_X$ServiceProvider`: X.`$ServiceProvider`
// internal     class `XY_Impl$dev_whyoleg_sweetspi_X$ServiceProvider`    : X.`$ServiceProvider`
// @Eager private val `XY_Impl$dev_whyoleg_sweetspi_X$ServiceProvider$initializer`

//
//@Provides(X::class)
//@Provides(Y::class)
//public object XY_Impl : X, Y {
//    internal class `$dev_whyoleg_sweetspi_X$ServiceProvider` : X.`$ServiceProvider` {
//        override val type: KClass<out X>
//            get() = XY_Impl::class
//
//        // delegate should be generated in a body: in case `XY_Impl` is a class, we should create new instance of a class every time we call `load`
//        // this is needed to mimic Java ServiceLoader behavior
//        private val delegate = lazy { XY_Impl }
//        override val value: X get() = delegate.value
//        override fun isInitialized(): Boolean = delegate.isInitialized()
//    }
//
//    internal class `$Provider$dev_whyoleg_sweetspi_Y` : Y.`$Provider` {
//        override val type: KClass<out Y>
//            get() = XY_Impl::class
//
//        // delegate should be generated in a body: in case `XY_Impl` is a class, we should create new instance of a class every time we call `load`
//        // this is needed to mimic Java ServiceLoader behavior
//        private val delegate = lazy { XY_Impl }
//        override val value: Y get() = delegate.value
//        override fun isInitialized(): Boolean = delegate.isInitialized()
//    }
//}

// test usage
//@ServiceProviderRegistryHolder(`X$ServiceProviderRegistry`::class) // generated
//@Service
//public interface X
// // generated
//public interface `X$ServiceProvider` : ServiceProvider<X>
//public object `X$ServiceProviderRegistry` : ServiceProviderRegistry<X>
//
//
//@Provides(X::class)
//@Provides(Y::class)
//public object X_Impl : X, Y
// //
//internal class `X_Impl$dev_whyoleg_sweetspi_X$ServiceProvider` : `X$ServiceProvider`
//private val `X_Impl$dev_whyoleg_sweetspi_X$initializer` = `X$ServiceProviderRegistry`.register(::`X_Impl$dev_whyoleg_sweetspi_X$ServiceProvider`)

// for wasm-js(multi-module) we could also generate:
//@JsExport fun module_name$services$dev_whyoleg_sweetspi_X(): JsReference<List<() -> ServiceProvider<X>>> {
//   return listOf(::`X_Impl$dev_whyoleg_sweetspi_X$ServiceProvider`).toJsReference()
//}
// for js/wasm-js via @JsExport we also handle any interfaces annotated with it


//// just service locator...
//internal class ServiceRegistry {
//    private val registries: MutableMap<String, ServiceProviderRegistry<*>> = mutableMapOf()
//    fun <T : Any> register(serviceType: KClass<T>, initializer: () -> ServiceProvider<T>) {
//        (registries.getOrPut(serviceType.qualifiedName!!, { object : ServiceProviderRegistry<T>() {} }) as ServiceProviderRegistry<T>)
//            .register(initializer)
//    }
//}
//
//// should provide manifest and fetched js files, right?
//public class JsModules {
//    internal val registries: Map<String, ServiceProviderRegistry<*>> = mutableMapOf()
//}
//
//// exported service is:
//// - FQN
//// - annotations
//// - initializer
//
//@JsExport
//public class JsService(
//    public val qualifiedName: String,
//    public val annotations: List<Annotation>, // not really, just specific JSON objects
//    public val initializer: () -> ServiceProvider<*>,
//)
//
////@JsExport // TODO is it needed?
//internal external class JsExportedServices : JsAny {
//    val registries: Map<String, ServiceProviderRegistry<*>>
//}
//
///**
// * Let's think about how we can support JS and Wasm:
// * - one way is to make `@JsExport X` working via js for both js and wasm-js
// * - another way is to use wasm multi-module compilation
// * - third way is to use wasm WIT, so as if we had `@WitExport X`
// */
//
//@JsExport
//public interface JsExportedService {
//    public fun x(some: Some): String
//}
//
//@JsExport
//public class Some
//
//@JsExport
//@Priority(123)
//@Provides(JsExportedService::class)
//public object JsExportedServiceImpl : JsExportedService {
//    override fun x(some: Some): String {
//        TODO("Not yet implemented")
//    }
//}
//
////@JsExport - TODO: this is not working. for now we could generate `Priority_JsExported`
//@ServiceInfo
//public annotation class Priority(val priority: Int)
//
//// generated
//public class Priority_JsExported(public val priority: Int)
//
//
////@OptIn(ExperimentalWasmJsInterop::class)
////@JsName("AsyncIterator")
////internal external interface JsAsyncIterator<out T : JsAny> : JsAny {
////    public fun next(): Promise<JsIteratorResult<T>>
////    public fun `return`(value: @UnsafeVariance T = definedExternally): Promise<JsIteratorResult<T>>
////    public fun `throw`(value: Any? = definedExternally): Promise<JsIteratorResult<T>>
////}
////
////@OptIn(ExperimentalWasmJsInterop::class)
////@JsName("IteratorResult")
////internal external interface JsIteratorResult<out T : JsAny> : JsAny {
////    public val value: T
////    public val done: Boolean
////}
//

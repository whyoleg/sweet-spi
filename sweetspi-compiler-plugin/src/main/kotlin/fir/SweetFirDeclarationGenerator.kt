/*
 * Copyright (c) 2026 Oleg Yukhnevich. Use of this source code is governed by the Apache 2.0 license.
 */

package dev.whyoleg.sweetspi.compiler.fir

import dev.whyoleg.sweetspi.compiler.*
import org.jetbrains.kotlin.descriptors.*
import org.jetbrains.kotlin.fir.*
import org.jetbrains.kotlin.fir.declarations.*
import org.jetbrains.kotlin.fir.expressions.*
import org.jetbrains.kotlin.fir.expressions.builder.*
import org.jetbrains.kotlin.fir.expressions.impl.*
import org.jetbrains.kotlin.fir.extensions.*
import org.jetbrains.kotlin.fir.extensions.predicate.*
import org.jetbrains.kotlin.fir.plugin.*
import org.jetbrains.kotlin.fir.symbols.impl.*
import org.jetbrains.kotlin.fir.types.*
import org.jetbrains.kotlin.fir.types.builder.*
import org.jetbrains.kotlin.name.*
import org.jetbrains.kotlin.platform.jvm.*

// Generates the ABI-visible nested declarations for each `@Service` class, so that dependent modules
// can resolve them across module boundaries (which IR-only generation cannot provide):
//  - (common) `X.$ServiceProvider`         : ServiceProvider<X>          — interface
//  - (klib)   `X.$ServiceProviderRegistry` : ServiceProviderRegistry<X>  — object (+ private ctor)
// Bodies for `@Provides` impl classes, and the klib `@ServiceProviderRegistryHolder` annotation on the
// service, are still produced in IR.
class SweetFirDeclarationGenerator(session: FirSession) : FirDeclarationGenerationExtension(session) {

    private val servicePredicate = DeclarationPredicate.create { annotated(SweetNames.Service.asSingleFqName()) }

    // klib (native/js/wasm) needs the associated-object registry; JVM uses META-INF/services instead.
    private val isKlib: Boolean = !session.moduleData.platform.isJvm()

    override fun FirDeclarationPredicateRegistrar.registerPredicates() {
        register(servicePredicate)
    }

    private fun FirClassSymbol<*>.isService(): Boolean =
        session.predicateBasedProvider.matches(servicePredicate, this)

    private fun FirClassSymbol<*>.isGeneratedRegistry(): Boolean {
        val origin = origin
        return classId.shortClassName == SweetNames.ServiceProviderRegistryName &&
                origin is FirDeclarationOrigin.Plugin && origin.key == SweetGeneratedDeclarationKey
    }

    override fun getNestedClassifiersNames(
        classSymbol: FirClassSymbol<*>,
        context: NestedClassGenerationContext,
    ): Set<Name> {
        if (!classSymbol.isService()) return emptySet()
        return buildSet {
            add(SweetNames.ServiceProviderName)
            if (isKlib) add(SweetNames.ServiceProviderRegistryName)
        }
    }

    override fun generateNestedClassLikeDeclaration(
        owner: FirClassSymbol<*>,
        name: Name,
        context: NestedClassGenerationContext,
    ): FirClassLikeSymbol<*>? {
        if (!owner.isService()) return null
        return when (name) {
            SweetNames.ServiceProviderName         -> generateServiceProvider(owner).symbol
            SweetNames.ServiceProviderRegistryName  -> generateServiceProviderRegistry(owner).symbol
            else                                    -> null
        }
    }

    override fun getCallableNamesForClass(
        classSymbol: FirClassSymbol<*>,
        context: MemberGenerationContext,
    ): Set<Name> = when {
        classSymbol.isGeneratedRegistry() -> setOf(SpecialNames.INIT)
        else                              -> emptySet()
    }

    override fun generateConstructors(context: MemberGenerationContext): List<FirConstructorSymbol> {
        val owner = context.owner
        if (!owner.isGeneratedRegistry()) return emptyList()
        // private primary ctor delegating to ServiceProviderRegistry<X>() (no-arg superclass ctor)
        return listOf(createDefaultPrivateConstructor(owner, SweetGeneratedDeclarationKey).symbol)
    }

    // interface X.$ServiceProvider : ServiceProvider<X>
    private fun generateServiceProvider(owner: FirClassSymbol<*>): FirRegularClass {
        return createNestedClass(
            owner = owner,
            name = SweetNames.ServiceProviderName,
            key = SweetGeneratedDeclarationKey,
            classKind = ClassKind.INTERFACE,
        ) {
            modality = Modality.ABSTRACT
            visibility = Visibilities.Internal
            superType(SweetNames.ServiceProvider.constructClassLikeType(arrayOf(owner.constructStarProjectedType())))
        }.also(::markPublishedApi)
    }

    // object X.$ServiceProviderRegistry : ServiceProviderRegistry<X>()
    private fun generateServiceProviderRegistry(owner: FirClassSymbol<*>): FirRegularClass {
        return createNestedClass(
            owner = owner,
            name = SweetNames.ServiceProviderRegistryName,
            key = SweetGeneratedDeclarationKey,
            classKind = ClassKind.OBJECT,
        ) {
            modality = Modality.FINAL
            visibility = Visibilities.Internal
            superType(SweetNames.ServiceProviderRegistry.constructClassLikeType(arrayOf(owner.constructStarProjectedType())))
        }.also(::markPublishedApi)
    }

    // `internal` nested types must be `@PublishedApi` to be referenceable from generated code in other modules.
    private fun markPublishedApi(klass: FirRegularClass) {
        klass.replaceAnnotations(
            klass.annotations + buildAnnotation {
                annotationTypeRef = buildResolvedTypeRef {
                    coneType = StandardClassIds.Annotations.PublishedApi.constructClassLikeType()
                }
                argumentMapping = FirEmptyAnnotationArgumentMapping
            }
        )
    }
}

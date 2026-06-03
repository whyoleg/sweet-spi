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

// Generates the ABI-visible nested declarations for each `@Service` class, so that dependent modules
// can resolve them across module boundaries (which IR-only generation cannot provide):
//  - (common) `X.$ServiceProvider` : ServiceProvider<X>           — interface
// Bodies (and `@Provides` impl classes) are still produced in IR.
class SweetFirDeclarationGenerator(session: FirSession) : FirDeclarationGenerationExtension(session) {

    private val servicePredicate = DeclarationPredicate.create { annotated(SweetNames.Service.asSingleFqName()) }

    override fun FirDeclarationPredicateRegistrar.registerPredicates() {
        register(servicePredicate)
    }

    private fun FirClassSymbol<*>.isService(): Boolean =
        session.predicateBasedProvider.matches(servicePredicate, this)

    override fun getNestedClassifiersNames(
        classSymbol: FirClassSymbol<*>,
        context: NestedClassGenerationContext,
    ): Set<Name> {
        if (!classSymbol.isService()) return emptySet()
        return setOf(SweetNames.ServiceProviderName)
    }

    override fun generateNestedClassLikeDeclaration(
        owner: FirClassSymbol<*>,
        name: Name,
        context: NestedClassGenerationContext,
    ): FirClassLikeSymbol<*>? {
        if (!owner.isService()) return null
        return when (name) {
            SweetNames.ServiceProviderName -> generateServiceProvider(owner).symbol
            else                           -> null
        }
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

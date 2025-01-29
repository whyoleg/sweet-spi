/*
 * Copyright (c) 2025 Oleg Yukhnevich. Use of this source code is governed by the Apache 2.0 license.
 */

package dev.whyoleg.sweetspi.compiler.fir

import dev.whyoleg.sweetspi.compiler.common.*
import org.jetbrains.kotlin.descriptors.*
import org.jetbrains.kotlin.fir.*
import org.jetbrains.kotlin.fir.extensions.*
import org.jetbrains.kotlin.fir.extensions.predicate.*
import org.jetbrains.kotlin.fir.plugin.*
import org.jetbrains.kotlin.fir.symbols.impl.*
import org.jetbrains.kotlin.name.*

@OptIn(ExperimentalTopLevelDeclarationsGenerationApi::class)
class KlibServiceProviderRegistrationGenerator(session: FirSession) : FirDeclarationGenerationExtension(session) {
    companion object {
        private val PREDICATE = LookupPredicate.create {
            annotated(SweetClassIds.ServiceProvider.asSingleFqName())
        }
    }

    // TODO: not only classes
    private val serviceProviderClasses by lazy {
        session.predicateBasedProvider
            .getSymbolsByPredicate(PREDICATE)
            .filterIsInstance<FirRegularClassSymbol>()
            .associateBy {
                CallableId(
                    it.classId.packageFqName,
                    Name.identifier("registerServiceProvider\$${it.classId.shortClassName}")
                )
            }
    }

    override fun getTopLevelCallableIds(): Set<CallableId> = serviceProviderClasses.keys

    override fun FirDeclarationPredicateRegistrar.registerPredicates() {
        register(PREDICATE)
    }

    override fun generateProperties(callableId: CallableId, context: MemberGenerationContext?): List<FirPropertySymbol> {
        if (context != null) return emptyList()
        if (!serviceProviderClasses.containsKey(callableId)) return emptyList()

        // TODO: handle JS
        val property = createTopLevelProperty(SweetDeclarationKey, callableId, session.builtinTypes.unitType.type) {
            visibility = Visibilities.Private
        }.apply {
            addEagerInitializationAnnotation(session)
        }

        return listOf(property.symbol)
    }

}

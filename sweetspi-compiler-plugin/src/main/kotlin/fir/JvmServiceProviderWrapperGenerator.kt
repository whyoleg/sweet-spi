/*
 * Copyright (c) 2025 Oleg Yukhnevich. Use of this source code is governed by the Apache 2.0 license.
 */

package dev.whyoleg.sweetspi.compiler.fir

import dev.whyoleg.sweetspi.compiler.common.*
import org.jetbrains.kotlin.descriptors.*
import org.jetbrains.kotlin.fir.*
import org.jetbrains.kotlin.fir.declarations.*
import org.jetbrains.kotlin.fir.expressions.*
import org.jetbrains.kotlin.fir.extensions.*
import org.jetbrains.kotlin.fir.extensions.predicate.*
import org.jetbrains.kotlin.fir.plugin.*
import org.jetbrains.kotlin.fir.resolve.*
import org.jetbrains.kotlin.fir.symbols.impl.*
import org.jetbrains.kotlin.fir.types.*
import org.jetbrains.kotlin.name.*

@OptIn(ExperimentalTopLevelDeclarationsGenerationApi::class)
class JvmServiceProviderWrapperGenerator(session: FirSession) : FirDeclarationGenerationExtension(session) {
    companion object {
        private val PREDICATE = LookupPredicate.create {
            annotated(SweetClassIds.ServiceProvider.asSingleFqName())
        }
    }

    // TODO: not only classes can be annotated
    private val serviceProviderClasses by lazy {
        session.predicateBasedProvider
            .getSymbolsByPredicate(PREDICATE)
            .filterIsInstance<FirRegularClassSymbol>()
            .associateBy { SweetClassIds.wrapper(it.classId) }
    }

    override fun getTopLevelClassIds(): Set<ClassId> = serviceProviderClasses.keys

    override fun FirDeclarationPredicateRegistrar.registerPredicates() {
        register(PREDICATE)
    }

    override fun generateTopLevelClassLikeDeclaration(classId: ClassId): FirClassLikeSymbol<*>? {
        val serviceProviderClass = serviceProviderClasses[classId] ?: return null

        val serviceTypes =
            serviceProviderClass.resolvedAnnotationsWithArguments
                .getAnnotationByClassId(SweetClassIds.ServiceProvider, session)
                ?.findArgumentByName(
                    SweetServiceProviderParameterNames.services,
                    returnFirstWhenNotFound = false
                )
                ?.evaluateAs<FirVarargArgumentsExpression>(session)
                ?.arguments
                ?.flatMap {
                    when (it) {
                        is FirSpreadArgumentExpression -> {
                            (it.expression as? FirArrayLiteral)?.arguments.orEmpty().filterIsInstance<FirGetClassCall>()
                        }
                        is FirGetClassCall             -> listOf(it)
                        else                           -> emptyList()
                    }
                }
                ?.mapNotNull(FirGetClassCall::getTargetType)
                .orEmpty()
                .ifEmpty {
                    serviceProviderClass.getSuperTypes(session).filter {
                        it.toRegularClassSymbol(session)?.hasAnnotation(SweetClassIds.Service, session) == true
                    }
                }
                .mapNotNull(ConeKotlinType::classId)
                .map(SweetClassIds::wrapper)
                .map(ClassId::constructClassLikeType)

        return createTopLevelClass(classId, SweetDeclarationKey, ClassKind.INTERFACE) {
            visibility = Visibilities.Internal
            modality = Modality.ABSTRACT

            // Service$Wrapper
            serviceTypes.forEach {
                superType(it)
            }
        }.apply {
            // TODO: should it have @PublishedApi?
            addPublishedApiAnnotation(session)
        }.symbol
    }
}

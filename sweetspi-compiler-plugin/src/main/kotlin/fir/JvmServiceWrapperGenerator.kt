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
import org.jetbrains.kotlin.fir.resolve.*
import org.jetbrains.kotlin.fir.symbols.impl.*
import org.jetbrains.kotlin.fir.types.*
import org.jetbrains.kotlin.name.*

@OptIn(ExperimentalTopLevelDeclarationsGenerationApi::class)
class JvmServiceWrapperGenerator(session: FirSession) : FirDeclarationGenerationExtension(session) {

    companion object {
        private val PREDICATE = LookupPredicate.create {
            annotated(SweetClassIds.Service.asSingleFqName())
        }
    }

    private val serviceClasses by lazy {
//        if (session.moduleData.platform.isJvm()) {
        session.predicateBasedProvider
            .getSymbolsByPredicate(PREDICATE)
            .filterIsInstance<FirRegularClassSymbol>()
            .associateBy { SweetClassIds.wrapper(it.classId) }
//        } else {
//            emptyMap()
//        }
    }

    override fun getTopLevelClassIds(): Set<ClassId> = serviceClasses.keys

    override fun FirDeclarationPredicateRegistrar.registerPredicates() {
        register(PREDICATE)
    }

    override fun generateTopLevelClassLikeDeclaration(classId: ClassId): FirClassLikeSymbol<*>? {
        val serviceClass = serviceClasses[classId] ?: return null

        return createTopLevelClass(classId, SweetDeclarationKey, ClassKind.INTERFACE) {
            visibility = Visibilities.Internal
            modality = Modality.ABSTRACT

            // () -> matchedClass
            superType(StandardClassIds.FunctionN(0).constructClassLikeType(arrayOf(serviceClass.defaultType())))
        }.apply {
            addPublishedApiAnnotation(session)
        }.symbol
    }

}

/*
 * Copyright (c) 2025 Oleg Yukhnevich. Use of this source code is governed by the Apache 2.0 license.
 */

package dev.whyoleg.sweetspi.compiler.fir

import org.jetbrains.kotlin.fir.*
import org.jetbrains.kotlin.fir.declarations.*
import org.jetbrains.kotlin.fir.expressions.builder.*
import org.jetbrains.kotlin.fir.references.builder.*
import org.jetbrains.kotlin.fir.resolve.*
import org.jetbrains.kotlin.fir.resolve.providers.*
import org.jetbrains.kotlin.fir.symbols.impl.*
import org.jetbrains.kotlin.fir.types.builder.*
import org.jetbrains.kotlin.name.*
import org.jetbrains.kotlin.platform.*
import org.jetbrains.kotlin.platform.konan.*

fun FirProperty.addEagerInitializationAnnotation(session: FirSession) {
    val platform = session.moduleData.platform
    val annotationName = when {
        platform.isNative() -> "kotlin.native.EagerInitialization"
        platform.isJs()     -> "kotlin.js.EagerInitialization"
        platform.isWasm()   -> "kotlin.EagerInitialization"
        else                -> return // should not happen
    }
    addAnnotation(session, ClassId.topLevel(FqName(annotationName)))
}

fun FirRegularClass.addPublishedApiAnnotation(session: FirSession) {
    addAnnotation(session, StandardClassIds.Annotations.PublishedApi)
}

private fun FirDeclaration.addAnnotation(
    session: FirSession,
    annotationClassId: ClassId,
) {
    val annotationSymbol = session.symbolProvider.getClassLikeSymbolByClassId(annotationClassId) as? FirRegularClassSymbol ?: return
    val constructorSymbol = annotationSymbol.declarationSymbols.firstNotNullOfOrNull { it as? FirConstructorSymbol } ?: return

    replaceAnnotations(annotations + buildAnnotationCall {
        annotationTypeRef = buildResolvedTypeRef {
            type = annotationSymbol.defaultType()
        }
        calleeReference = buildResolvedNamedReference {
            name = annotationSymbol.name
            resolvedSymbol = constructorSymbol
        }
        containingDeclarationSymbol = symbol
    })
}

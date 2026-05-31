/*
 * Copyright (c) 2026 Oleg Yukhnevich. Use of this source code is governed by the Apache 2.0 license.
 */

package dev.whyoleg.sweetspi.compiler.checkers

import dev.whyoleg.sweetspi.compiler.SweetNames
import org.jetbrains.kotlin.descriptors.ClassKind
import org.jetbrains.kotlin.diagnostics.DiagnosticReporter
import org.jetbrains.kotlin.diagnostics.reportOn
import org.jetbrains.kotlin.fir.analysis.checkers.MppCheckerKind
import org.jetbrains.kotlin.fir.analysis.checkers.context.CheckerContext
import org.jetbrains.kotlin.fir.analysis.checkers.declaration.FirPropertyChecker
import org.jetbrains.kotlin.fir.declarations.FirProperty
import org.jetbrains.kotlin.fir.declarations.hasAnnotation
import org.jetbrains.kotlin.fir.resolve.getContainingClassSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirClassSymbol
import org.jetbrains.kotlin.fir.types.ConeTypeParameterType

internal object ProvidesPropertyChecker : FirPropertyChecker(MppCheckerKind.Common) {
    context(context: CheckerContext, reporter: DiagnosticReporter)
    override fun check(declaration: FirProperty) {
        val session = context.session
        if (!declaration.hasAnnotation(SweetNames.Provides, session)) return

        if (!declaration.symbol.resolvedStatus.visibility.isAllowedForSweet()) {
            reporter.reportOn(declaration.source, SweetDiagnostics.PROVIDES_WRONG_VISIBILITY)
        }
        if (declaration.symbol.isVar) {
            reporter.reportOn(declaration.source, SweetDiagnostics.PROVIDES_PROPERTY_MUTABLE)
        }
        if (declaration.symbol.receiverParameterSymbol != null || declaration.symbol.contextParameterSymbols.isNotEmpty()) {
            reporter.reportOn(declaration.source, SweetDiagnostics.PROVIDES_PROPERTY_RECEIVER)
        }

        val container = declaration.symbol.getContainingClassSymbol()
        val allowedContainer = container == null || (container is FirClassSymbol<*> && container.classKind == ClassKind.OBJECT)
        if (!allowedContainer) {
            reporter.reportOn(declaration.source, SweetDiagnostics.PROVIDES_PROPERTY_NOT_TOP_LEVEL_OR_OBJECT)
        }

        declaration.reportDuplicateProvidesServices(session)

        val returnType = declaration.symbol.resolvedReturnType
        val isTypeParameter = returnType is ConeTypeParameterType
        val providedSymbol = returnType.serviceClassSymbol(session)
        declaration.providesServiceTypes(session).forEach { (annotation, serviceType) ->
            val serviceSymbol = serviceType.serviceClassSymbol(session) ?: return@forEach
            when {
                !serviceSymbol.isAnnotatedAsService(session) ->
                    reporter.reportOn(annotation.source, SweetDiagnostics.PROVIDES_SERVICE_NOT_A_SERVICE, serviceSymbol.classId.asFqNameString())
                isTypeParameter ->
                    reporter.reportOn(declaration.source, SweetDiagnostics.PROVIDES_TYPE_PARAMETER)
                providedSymbol == null || !providedSymbol.isSweetSubclassOf(serviceSymbol.classId, session) ->
                    reporter.reportOn(declaration.source, SweetDiagnostics.PROVIDES_TYPE_MISMATCH, serviceSymbol.classId.asFqNameString())
            }
        }
    }
}

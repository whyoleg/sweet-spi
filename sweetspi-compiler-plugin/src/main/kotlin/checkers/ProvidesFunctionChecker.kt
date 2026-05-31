/*
 * Copyright (c) 2026 Oleg Yukhnevich. Use of this source code is governed by the Apache 2.0 license.
 */

package dev.whyoleg.sweetspi.compiler.checkers

import dev.whyoleg.sweetspi.compiler.SweetNames
import org.jetbrains.kotlin.diagnostics.DiagnosticReporter
import org.jetbrains.kotlin.diagnostics.reportOn
import org.jetbrains.kotlin.fir.analysis.checkers.MppCheckerKind
import org.jetbrains.kotlin.fir.analysis.checkers.context.CheckerContext
import org.jetbrains.kotlin.fir.analysis.checkers.declaration.FirSimpleFunctionChecker
import org.jetbrains.kotlin.fir.declarations.FirNamedFunction
import org.jetbrains.kotlin.fir.declarations.hasAnnotation

internal object ProvidesFunctionChecker : FirSimpleFunctionChecker(MppCheckerKind.Common) {
    context(context: CheckerContext, reporter: DiagnosticReporter)
    override fun check(declaration: FirNamedFunction) {
        val session = context.session
        if (!declaration.hasAnnotation(SweetNames.Provides, session)) return

        val symbol = declaration.symbol

        if (!symbol.resolvedStatus.visibility.isAllowedForSweet()) {
            reporter.reportOn(declaration.source, SweetDiagnostics.PROVIDES_WRONG_VISIBILITY)
        }
        if (symbol.receiverParameterSymbol != null || symbol.contextParameterSymbols.isNotEmpty()) {
            reporter.reportOn(declaration.source, SweetDiagnostics.PROVIDES_FUNCTION_RECEIVER)
        }
        if (symbol.resolvedStatus.isSuspend) {
            reporter.reportOn(declaration.source, SweetDiagnostics.PROVIDES_FUNCTION_SUSPEND)
        }
        if (symbol.valueParameterSymbols.any { !it.hasDefaultValue }) {
            reporter.reportOn(declaration.source, SweetDiagnostics.PROVIDES_FUNCTION_PARAMETERS)
        }

        val providedSymbol = symbol.resolvedReturnType.serviceClassSymbol(session)
        declaration.providesServiceTypes(session).forEach { (annotation, serviceType) ->
            val serviceSymbol = serviceType.serviceClassSymbol(session) ?: return@forEach
            if (!serviceSymbol.isAnnotatedAsService(session)) {
                reporter.reportOn(annotation.source, SweetDiagnostics.PROVIDES_SERVICE_NOT_A_SERVICE, serviceSymbol.classId.asFqNameString())
            } else if (providedSymbol == null || !providedSymbol.isSweetSubclassOf(serviceSymbol.classId, session)) {
                reporter.reportOn(declaration.source, SweetDiagnostics.PROVIDES_TYPE_MISMATCH, serviceSymbol.classId.asFqNameString())
            }
        }
    }
}

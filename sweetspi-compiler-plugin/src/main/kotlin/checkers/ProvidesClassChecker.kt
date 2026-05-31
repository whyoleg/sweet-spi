/*
 * Copyright (c) 2026 Oleg Yukhnevich. Use of this source code is governed by the Apache 2.0 license.
 */

package dev.whyoleg.sweetspi.compiler.checkers

import dev.whyoleg.sweetspi.compiler.SweetNames
import org.jetbrains.kotlin.fir.FirSession
import org.jetbrains.kotlin.descriptors.ClassKind
import org.jetbrains.kotlin.descriptors.Modality
import org.jetbrains.kotlin.descriptors.Visibilities
import org.jetbrains.kotlin.diagnostics.DiagnosticReporter
import org.jetbrains.kotlin.diagnostics.reportOn
import org.jetbrains.kotlin.fir.analysis.checkers.MppCheckerKind
import org.jetbrains.kotlin.fir.analysis.checkers.context.CheckerContext
import org.jetbrains.kotlin.fir.analysis.checkers.declaration.FirRegularClassChecker
import org.jetbrains.kotlin.fir.declarations.FirRegularClass
import org.jetbrains.kotlin.fir.declarations.constructors
import org.jetbrains.kotlin.fir.declarations.hasAnnotation
import org.jetbrains.kotlin.fir.declarations.utils.modality
import org.jetbrains.kotlin.fir.symbols.impl.FirConstructorSymbol

internal object ProvidesClassChecker : FirRegularClassChecker(MppCheckerKind.Common) {
    context(context: CheckerContext, reporter: DiagnosticReporter)
    override fun check(declaration: FirRegularClass) {
        val session = context.session
        if (!declaration.hasAnnotation(SweetNames.Provides, session)) return

        // cross-check: not both @Service and @Provides
        if (declaration.hasAnnotation(SweetNames.Service, session)) {
            reporter.reportOn(declaration.source, SweetDiagnostics.PROVIDES_AND_SERVICE_TOGETHER)
        }

        // visibility
        if (!declaration.symbol.resolvedStatus.visibility.isAllowedForSweet()) {
            reporter.reportOn(declaration.source, SweetDiagnostics.PROVIDES_WRONG_VISIBILITY)
        }

        // local / inner
        if (declaration.isLocal) {
            reporter.reportOn(declaration.source, SweetDiagnostics.PROVIDES_CLASS_LOCAL)
        }
        if (declaration.symbol.resolvedStatus.isInner) {
            reporter.reportOn(declaration.source, SweetDiagnostics.PROVIDES_CLASS_INNER)
        }

        // instantiable kind
        val instantiable = when (declaration.classKind) {
            ClassKind.OBJECT -> true
            ClassKind.CLASS -> declaration.modality != Modality.ABSTRACT && declaration.modality != Modality.SEALED
            else -> false // INTERFACE, ENUM_CLASS, ANNOTATION_CLASS
        }
        if (!instantiable) {
            reporter.reportOn(declaration.source, SweetDiagnostics.PROVIDES_CLASS_NOT_INSTANTIABLE)
        } else if (declaration.classKind == ClassKind.CLASS && !hasSuitableConstructor(declaration, session)) {
            reporter.reportOn(declaration.source, SweetDiagnostics.PROVIDES_CLASS_NO_CONSTRUCTOR)
        }

        // shared @Provides(X) checks: X is a @Service and the class is a subtype of X
        declaration.providesServiceTypes(session).forEach { (annotation, serviceType) ->
            val serviceSymbol = serviceType.serviceClassSymbol(session) ?: return@forEach
            if (!serviceSymbol.isAnnotatedAsService(session)) {
                reporter.reportOn(annotation.source, SweetDiagnostics.PROVIDES_SERVICE_NOT_A_SERVICE, serviceSymbol.classId.asFqNameString())
            } else if (!declaration.symbol.isSweetSubclassOf(serviceSymbol.classId, session)) {
                reporter.reportOn(declaration.source, SweetDiagnostics.PROVIDES_TYPE_MISMATCH, serviceSymbol.classId.asFqNameString())
            }
        }
    }

    private fun hasSuitableConstructor(declaration: FirRegularClass, session: FirSession): Boolean {
        val constructors = declaration.symbol.constructors(session).filter { ctor: FirConstructorSymbol ->
            val vis = ctor.resolvedStatus.visibility
            vis == Visibilities.Public || vis == Visibilities.Internal
        }
        return constructors.any { ctor ->
            ctor.valueParameterSymbols.isEmpty() || ctor.valueParameterSymbols.all { it.hasDefaultValue }
        }
    }
}

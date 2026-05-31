/*
 * Copyright (c) 2026 Oleg Yukhnevich. Use of this source code is governed by the Apache 2.0 license.
 */

package dev.whyoleg.sweetspi.compiler.checkers

import dev.whyoleg.sweetspi.compiler.SweetNames
import org.jetbrains.kotlin.descriptors.ClassKind
import org.jetbrains.kotlin.descriptors.Modality
import org.jetbrains.kotlin.diagnostics.DiagnosticReporter
import org.jetbrains.kotlin.diagnostics.reportOn
import org.jetbrains.kotlin.fir.analysis.checkers.MppCheckerKind
import org.jetbrains.kotlin.fir.analysis.checkers.context.CheckerContext
import org.jetbrains.kotlin.fir.analysis.checkers.declaration.FirRegularClassChecker
import org.jetbrains.kotlin.fir.declarations.FirRegularClass
import org.jetbrains.kotlin.fir.declarations.hasAnnotation
import org.jetbrains.kotlin.fir.declarations.utils.modality

internal object ServiceChecker : FirRegularClassChecker(MppCheckerKind.Common) {
    context(context: CheckerContext, reporter: DiagnosticReporter)
    override fun check(declaration: FirRegularClass) {
        val session = context.session
        if (!declaration.hasAnnotation(SweetNames.Service, session)) return

        val isInterfaceOrAbstractClass = declaration.classKind == ClassKind.INTERFACE ||
                (declaration.classKind == ClassKind.CLASS && declaration.modality == Modality.ABSTRACT)
        if (!isInterfaceOrAbstractClass) {
            reporter.reportOn(declaration.source, SweetDiagnostics.SERVICE_WRONG_TARGET)
        }

        if (!declaration.status.visibility.isAllowedForSweet()) {
            reporter.reportOn(declaration.source, SweetDiagnostics.SERVICE_WRONG_VISIBILITY)
        }
    }
}

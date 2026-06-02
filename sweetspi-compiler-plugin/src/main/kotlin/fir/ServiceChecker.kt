/*
 * Copyright (c) 2026 Oleg Yukhnevich. Use of this source code is governed by the Apache 2.0 license.
 */

package dev.whyoleg.sweetspi.compiler.fir

import dev.whyoleg.sweetspi.compiler.*
import org.jetbrains.kotlin.descriptors.*
import org.jetbrains.kotlin.diagnostics.*
import org.jetbrains.kotlin.fir.analysis.checkers.*
import org.jetbrains.kotlin.fir.analysis.checkers.context.*
import org.jetbrains.kotlin.fir.analysis.checkers.declaration.*
import org.jetbrains.kotlin.fir.declarations.*
import org.jetbrains.kotlin.fir.declarations.utils.*

object ServiceChecker : FirRegularClassChecker(MppCheckerKind.Common) {
    context(context: CheckerContext, reporter: DiagnosticReporter)
    override fun check(declaration: FirRegularClass) {
        val session = context.session
        if (!declaration.hasAnnotation(SweetNames.Service, session)) return

        // TODO: java also support `open class`
        // TODO: we could also support final classes, enums and sealed classes - only for objects it doesn't make sense
        val isInterfaceOrAbstractClass = declaration.classKind == ClassKind.INTERFACE ||
                (declaration.classKind == ClassKind.CLASS && declaration.modality == Modality.ABSTRACT)
        if (!isInterfaceOrAbstractClass) {
            reporter.reportOn(declaration.source, SweetDiagnostics.SERVICE_WRONG_TARGET)
        }

        if (!declaration.symbol.resolvedStatus.visibility.isPublicOrInternal()) {
            reporter.reportOn(declaration.source, SweetDiagnostics.SERVICE_WRONG_VISIBILITY)
        }
    }
}

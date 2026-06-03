/*
 * Copyright (c) 2026 Oleg Yukhnevich. Use of this source code is governed by the Apache 2.0 license.
 */

package dev.whyoleg.sweetspi.compiler.fir

import dev.whyoleg.sweetspi.compiler.*
import org.jetbrains.kotlin.descriptors.*
import org.jetbrains.kotlin.diagnostics.*
import org.jetbrains.kotlin.fir.*
import org.jetbrains.kotlin.fir.analysis.checkers.*
import org.jetbrains.kotlin.fir.analysis.checkers.context.*
import org.jetbrains.kotlin.fir.analysis.checkers.declaration.*
import org.jetbrains.kotlin.fir.declarations.*
import org.jetbrains.kotlin.fir.declarations.utils.*
import org.jetbrains.kotlin.fir.resolve.*
import org.jetbrains.kotlin.fir.symbols.impl.*
import org.jetbrains.kotlin.name.*

// Shared visibility predicate used by both ProvidesChecker and ServiceChecker
fun Visibility.isPublicOrInternal(): Boolean =
    this == Visibilities.Public || this == Visibilities.Internal

// Reads @Service(javaServiceLoaderCompatibilityMode) off the service symbol; absent → NO_COMPATIBILITY.
// Works cross-module (deserialized annotation arguments are handled by extractEnumValueArgumentInfo).
fun FirClassSymbol<*>.jvmCompatMode(session: FirSession): JvmCompatMode {
    val entry = getAnnotationByClassId(SweetNames.Service, session)
        ?.findArgumentByName(SweetNames.ServiceCompatModeArgument)
        ?.extractEnumValueArgumentInfo()
        ?.enumEntryName
        ?.asString()
    return JvmCompatMode.fromEntryName(entry)
}

sealed class ProvidesChecker<D : FirMemberDeclaration> : FirDeclarationChecker<D>(MppCheckerKind.Common) {

    context(context: CheckerContext, reporter: DiagnosticReporter)
    final override fun check(declaration: D) {
        val session = context.session
        if (!declaration.hasAnnotation(SweetNames.Provides, session)) return

        if (!declaration.status.visibility.isPublicOrInternal()) {
            reporter.reportOn(declaration.source, SweetDiagnostics.PROVIDES_WRONG_VISIBILITY)
        }

        val seen = HashSet<ClassId>()
        val providedSymbol = providedClassSymbol(declaration, session)
        declaration.getAnnotationsByClassId(SweetNames.Provides, session).forEach { annotation ->
            val serviceType = annotation.getKClassArgument(SweetNames.ProvidesServiceArgument, session) ?: return@forEach
            val serviceSymbol = serviceType.toRegularClassSymbol(session) ?: return@forEach

            val id = serviceSymbol.classId
            if (!seen.add(id)) {
                reporter.reportOn(annotation.source, SweetDiagnostics.PROVIDES_DUPLICATE_SERVICE, id.asFqNameString())
            }

            when {
                !serviceSymbol.hasAnnotation(SweetNames.Service, session)                                                          ->
                    reporter.reportOn(
                        annotation.source,
                        SweetDiagnostics.PROVIDES_SERVICE_NOT_A_SERVICE,
                        serviceSymbol.classId.asFqNameString(),
                    )
                providedSymbol == null ||
                        !(providedSymbol.classId == serviceSymbol.classId || serviceSymbol.isSupertypeOf(providedSymbol, session)) -> {
//                    error("$serviceSymbol | $providedSymbol")
                    reporter.reportOn(
                        declaration.source,
                        SweetDiagnostics.PROVIDES_TYPE_MISMATCH,
                        serviceSymbol.classId.asFqNameString(),
                    )
                }
            }

            if (serviceSymbol.jvmCompatMode(session) == JvmCompatMode.FULL_COMPATIBILITY) {
                val isInstantiableClass = declaration is FirRegularClass && declaration.classKind == ClassKind.CLASS
                if (!isInstantiableClass) {
                    reporter.reportOn(
                        annotation.source,
                        SweetDiagnostics.PROVIDES_FULL_COMPATIBILITY_REQUIRES_CLASS,
                        serviceSymbol.classId.asFqNameString(),
                    )
                }
            }
        }

        check2(declaration)
    }

    context(context: CheckerContext, reporter: DiagnosticReporter)
    abstract fun check2(declaration: D)

    protected abstract fun providedClassSymbol(declaration: D, session: FirSession): FirClassSymbol<*>?
}

// ---------------------------------------------------------------------------
// Intermediate base for callable (function / property) providers
// ---------------------------------------------------------------------------

object ProvidesClassChecker : ProvidesChecker<FirRegularClass>() {
    override fun providedClassSymbol(declaration: FirRegularClass, session: FirSession): FirClassSymbol<*> = declaration.symbol

    context(context: CheckerContext, reporter: DiagnosticReporter)
    override fun check2(declaration: FirRegularClass) {
        val session = context.session

        // cross-check: not both @Service and @Provides
        if (declaration.hasAnnotation(SweetNames.Service, session)) {
            reporter.reportOn(declaration.source, SweetDiagnostics.PROVIDES_AND_SERVICE_TOGETHER)
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
            ClassKind.CLASS  -> declaration.modality != Modality.ABSTRACT && declaration.modality != Modality.SEALED
            else             -> false // INTERFACE, ENUM_CLASS, ANNOTATION_CLASS
        }
        if (!instantiable) {
            reporter.reportOn(declaration.source, SweetDiagnostics.PROVIDES_CLASS_NOT_INSTANTIABLE)
        } else if (declaration.classKind == ClassKind.CLASS && !hasSuitableConstructor(declaration, session)) {
            reporter.reportOn(declaration.source, SweetDiagnostics.PROVIDES_CLASS_NO_CONSTRUCTOR)
        }
    }

    private fun hasSuitableConstructor(declaration: FirRegularClass, session: FirSession): Boolean {
        val constructors = declaration.symbol.constructors(session).filter { ctor ->
            ctor.resolvedStatus.visibility.isPublicOrInternal()
        }
        return constructors.any { ctor ->
            ctor.valueParameterSymbols.isEmpty() || ctor.valueParameterSymbols.all { it.hasDefaultValue }
        }
    }
}

sealed class ProvidesCallableChecker<D : FirCallableDeclaration> : ProvidesChecker<D>() {
    final override fun providedClassSymbol(declaration: D, session: FirSession): FirClassSymbol<*>? =
        declaration.symbol.resolvedReturnType.toRegularClassSymbol(session)

    context(context: CheckerContext, reporter: DiagnosticReporter)
    override fun check2(declaration: D) {
        val container = declaration.getContainingClassSymbol()
        val allowedContainer = container == null || (container is FirClassSymbol<*> && container.classKind == ClassKind.OBJECT)
        if (!allowedContainer) {
            reporter.reportOn(declaration.source, SweetDiagnostics.PROVIDES_CALLABLE_NOT_TOP_LEVEL_OR_OBJECT)
        }
        if (declaration.symbol.receiverParameterSymbol != null || declaration.symbol.contextParameterSymbols.isNotEmpty()) {
            reporter.reportOn(declaration.source, SweetDiagnostics.PROVIDES_CALLABLE_PARAMETERS)
        }
    }
}

object ProvidesFunctionChecker : ProvidesCallableChecker<FirNamedFunction>() {
    context(context: CheckerContext, reporter: DiagnosticReporter)
    override fun check2(declaration: FirNamedFunction) {
        super.check2(declaration)

        if (declaration.symbol.resolvedStatus.isSuspend) {
            reporter.reportOn(declaration.source, SweetDiagnostics.PROVIDES_FUNCTION_SUSPEND)
        }
        if (declaration.symbol.valueParameterSymbols.any { !it.hasDefaultValue }) {
            reporter.reportOn(declaration.source, SweetDiagnostics.PROVIDES_CALLABLE_PARAMETERS)
        }
    }
}

object ProvidesPropertyChecker : ProvidesCallableChecker<FirProperty>() {
    context(context: CheckerContext, reporter: DiagnosticReporter)
    override fun check2(declaration: FirProperty) {
        super.check2(declaration)

        if (declaration.symbol.resolvedStatus.isSuspend || declaration.getter!!.symbol.resolvedStatus.isSuspend) {
            reporter.reportOn(declaration.source, SweetDiagnostics.PROVIDES_FUNCTION_SUSPEND)
        }
    }
}

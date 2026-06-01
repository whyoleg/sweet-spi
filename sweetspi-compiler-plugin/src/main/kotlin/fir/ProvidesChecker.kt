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
import org.jetbrains.kotlin.fir.expressions.*
import org.jetbrains.kotlin.fir.resolve.*
import org.jetbrains.kotlin.fir.symbols.impl.*
import org.jetbrains.kotlin.fir.types.*
import org.jetbrains.kotlin.name.*

// Shared visibility predicate used by both ProvidesChecker and ServiceChecker (same package, no import needed).
internal fun Visibility.isAllowedForSweet(): Boolean =
    this == Visibilities.Public || this == Visibilities.Internal

// ---------------------------------------------------------------------------
// Private helpers (only used by the @Provides checker hierarchy in this file)
// ---------------------------------------------------------------------------

/** Each `@Provides(service = X::class)` annotation on this declaration, paired with the resolved service type X. */
private fun FirMemberDeclaration.providesServiceTypes(session: FirSession): List<Pair<FirAnnotation, ConeKotlinType>> =
    getAnnotationsByClassId(SweetNames.Provides, session).mapNotNull { annotation ->
        val serviceType = annotation.getKClassArgument(SweetNames.ProvidesServiceArgument, session) ?: return@mapNotNull null
        annotation to serviceType
    }

/** The service type X resolved to its declaring class symbol, or null if unresolvable. */
private fun ConeKotlinType.serviceClassSymbol(session: FirSession): FirClassSymbol<*>? =
    toRegularClassSymbol(session)

/** True if X (the service) is itself annotated with @Service. */
private fun FirClassSymbol<*>.isAnnotatedAsService(session: FirSession): Boolean =
    hasAnnotation(SweetNames.Service, session)

/** True if `this` provider class is a subtype of the service identified by [serviceClassId] (compared by classifier, ignoring type arguments). */
private fun FirClassSymbol<*>.isSweetSubclassOf(serviceClassId: ClassId, session: FirSession): Boolean {
    if (classId == serviceClassId) return true
    return lookupSuperTypes(
        symbols = listOf(this),
        lookupInterfaces = true,
        deep = true,
        useSiteSession = session,
        substituteTypes = false,
    ).any { it.lookupTag.classId == serviceClassId }
}

/** Reports [SweetDiagnostics.PROVIDES_DUPLICATE_SERVICE] for any service type that appears more than once on this declaration. */
context(context: CheckerContext, reporter: DiagnosticReporter)
private fun FirMemberDeclaration.reportDuplicateServices(session: FirSession) {
    val seen = HashSet<ClassId>()
    providesServiceTypes(session).forEach { (annotation, serviceType) ->
        val id = serviceType.serviceClassSymbol(session)?.classId ?: return@forEach
        if (!seen.add(id)) {
            reporter.reportOn(annotation.source, SweetDiagnostics.PROVIDES_DUPLICATE_SERVICE, id.asFqNameString())
        }
    }
}

// ---------------------------------------------------------------------------
// Abstract base
// ---------------------------------------------------------------------------

internal abstract class ProvidesChecker<D : FirMemberDeclaration> : FirDeclarationChecker<D>(MppCheckerKind.Common) {

    context(context: CheckerContext, reporter: DiagnosticReporter)
    final override fun check(declaration: D) {
        val session = context.session
        if (!declaration.hasAnnotation(SweetNames.Provides, session)) return

        if (!declaration.status.visibility.isAllowedForSweet()) {
            reporter.reportOn(declaration.source, SweetDiagnostics.PROVIDES_WRONG_VISIBILITY)
        }

        checkTarget(declaration)

        declaration.reportDuplicateServices(session)

        val providedSymbol = providedClassSymbol(declaration, session)
        val isTypeParameter = providedTypeIsTypeParameter(declaration)
        declaration.providesServiceTypes(session).forEach { (annotation, serviceType) ->
            val serviceSymbol = serviceType.serviceClassSymbol(session) ?: return@forEach
            when {
                !serviceSymbol.isAnnotatedAsService(session)                                                ->
                    reporter.reportOn(
                        annotation.source,
                        SweetDiagnostics.PROVIDES_SERVICE_NOT_A_SERVICE,
                        serviceSymbol.classId.asFqNameString(),
                    )
                isTypeParameter                                                                             ->
                    reporter.reportOn(declaration.source, SweetDiagnostics.PROVIDES_TYPE_PARAMETER)
                providedSymbol == null || !providedSymbol.isSweetSubclassOf(serviceSymbol.classId, session) ->
                    reporter.reportOn(
                        declaration.source,
                        SweetDiagnostics.PROVIDES_TYPE_MISMATCH,
                        serviceSymbol.classId.asFqNameString(),
                    )
            }
        }
    }

    context(context: CheckerContext, reporter: DiagnosticReporter)
    protected abstract fun checkTarget(declaration: D)

    protected abstract fun providedClassSymbol(declaration: D, session: FirSession): FirClassSymbol<*>?

    protected open fun providedTypeIsTypeParameter(declaration: D): Boolean = false
}

// ---------------------------------------------------------------------------
// Intermediate base for callable (function / property) providers
// ---------------------------------------------------------------------------

internal abstract class ProvidesCallableChecker<D : FirCallableDeclaration> : ProvidesChecker<D>() {

    protected abstract val notTopLevelOrObjectDiagnostic: KtDiagnosticFactory0
    protected abstract val receiverDiagnostic: KtDiagnosticFactory0

    final override fun providedClassSymbol(declaration: D, session: FirSession): FirClassSymbol<*>? =
        declaration.symbol.resolvedReturnType.serviceClassSymbol(session)

    context(context: CheckerContext, reporter: DiagnosticReporter)
    final override fun checkTarget(declaration: D) {
        val container = declaration.symbol.getContainingClassSymbol()
        val allowedContainer = container == null || (container is FirClassSymbol<*> && container.classKind == ClassKind.OBJECT)
        if (!allowedContainer) {
            reporter.reportOn(declaration.source, notTopLevelOrObjectDiagnostic)
        }
        if (declaration.symbol.receiverParameterSymbol != null || declaration.symbol.contextParameterSymbols.isNotEmpty()) {
            reporter.reportOn(declaration.source, receiverDiagnostic)
        }
        checkCallable(declaration)
    }

    context(context: CheckerContext, reporter: DiagnosticReporter)
    protected abstract fun checkCallable(declaration: D)
}

// ---------------------------------------------------------------------------
// Concrete checkers
// ---------------------------------------------------------------------------

internal object ProvidesClassChecker : ProvidesChecker<FirRegularClass>() {

    context(context: CheckerContext, reporter: DiagnosticReporter)
    override fun checkTarget(declaration: FirRegularClass) {
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

    override fun providedClassSymbol(declaration: FirRegularClass, session: FirSession): FirClassSymbol<*> =
        declaration.symbol

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

internal object ProvidesFunctionChecker : ProvidesCallableChecker<FirNamedFunction>() {
    override val notTopLevelOrObjectDiagnostic get() = SweetDiagnostics.PROVIDES_FUNCTION_NOT_TOP_LEVEL_OR_OBJECT
    override val receiverDiagnostic get() = SweetDiagnostics.PROVIDES_FUNCTION_RECEIVER

    override fun providedTypeIsTypeParameter(declaration: FirNamedFunction): Boolean =
        declaration.symbol.resolvedReturnType is ConeTypeParameterType

    context(context: CheckerContext, reporter: DiagnosticReporter)
    override fun checkCallable(declaration: FirNamedFunction) {
        if (declaration.symbol.resolvedStatus.isSuspend) {
            reporter.reportOn(declaration.source, SweetDiagnostics.PROVIDES_FUNCTION_SUSPEND)
        }
        if (declaration.symbol.valueParameterSymbols.any { !it.hasDefaultValue }) {
            reporter.reportOn(declaration.source, SweetDiagnostics.PROVIDES_FUNCTION_PARAMETERS)
        }
    }
}

internal object ProvidesPropertyChecker : ProvidesCallableChecker<FirProperty>() {
    override val notTopLevelOrObjectDiagnostic get() = SweetDiagnostics.PROVIDES_PROPERTY_NOT_TOP_LEVEL_OR_OBJECT
    override val receiverDiagnostic get() = SweetDiagnostics.PROVIDES_PROPERTY_RECEIVER

    context(context: CheckerContext, reporter: DiagnosticReporter)
    override fun checkCallable(declaration: FirProperty) {
        if (declaration.symbol.isVar) {
            reporter.reportOn(declaration.source, SweetDiagnostics.PROVIDES_PROPERTY_MUTABLE)
        }
    }
}

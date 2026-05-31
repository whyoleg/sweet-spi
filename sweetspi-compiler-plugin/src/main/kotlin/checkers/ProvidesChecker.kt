/*
 * Copyright (c) 2026 Oleg Yukhnevich. Use of this source code is governed by the Apache 2.0 license.
 */

package dev.whyoleg.sweetspi.compiler.checkers

import dev.whyoleg.sweetspi.compiler.SweetNames
import org.jetbrains.kotlin.descriptors.ClassKind
import org.jetbrains.kotlin.descriptors.Modality
import org.jetbrains.kotlin.descriptors.Visibilities
import org.jetbrains.kotlin.descriptors.Visibility
import org.jetbrains.kotlin.diagnostics.DiagnosticReporter
import org.jetbrains.kotlin.diagnostics.reportOn
import org.jetbrains.kotlin.fir.FirSession
import org.jetbrains.kotlin.fir.analysis.checkers.MppCheckerKind
import org.jetbrains.kotlin.fir.analysis.checkers.context.CheckerContext
import org.jetbrains.kotlin.fir.analysis.checkers.declaration.FirDeclarationChecker
import org.jetbrains.kotlin.fir.declarations.FirMemberDeclaration
import org.jetbrains.kotlin.fir.declarations.FirNamedFunction
import org.jetbrains.kotlin.fir.declarations.FirProperty
import org.jetbrains.kotlin.fir.declarations.FirRegularClass
import org.jetbrains.kotlin.fir.declarations.constructors
import org.jetbrains.kotlin.fir.declarations.getAnnotationsByClassId
import org.jetbrains.kotlin.fir.declarations.getKClassArgument
import org.jetbrains.kotlin.fir.declarations.hasAnnotation
import org.jetbrains.kotlin.fir.declarations.utils.modality
import org.jetbrains.kotlin.fir.expressions.FirAnnotation
import org.jetbrains.kotlin.fir.resolve.getContainingClassSymbol
import org.jetbrains.kotlin.fir.resolve.lookupSuperTypes
import org.jetbrains.kotlin.fir.resolve.toRegularClassSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirClassSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirConstructorSymbol
import org.jetbrains.kotlin.fir.types.ConeKotlinType
import org.jetbrains.kotlin.fir.types.ConeTypeParameterType
import org.jetbrains.kotlin.name.ClassId

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

internal abstract class ProvidesChecker<D : FirMemberDeclaration>(
) : FirDeclarationChecker<D>(MppCheckerKind.Common) {

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
                !serviceSymbol.isAnnotatedAsService(session) ->
                    reporter.reportOn(
                        annotation.source,
                        SweetDiagnostics.PROVIDES_SERVICE_NOT_A_SERVICE,
                        serviceSymbol.classId.asFqNameString(),
                    )
                isTypeParameter ->
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
            ClassKind.CLASS -> declaration.modality != Modality.ABSTRACT && declaration.modality != Modality.SEALED
            else -> false // INTERFACE, ENUM_CLASS, ANNOTATION_CLASS
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

internal object ProvidesFunctionChecker : ProvidesChecker<FirNamedFunction>() {

    context(context: CheckerContext, reporter: DiagnosticReporter)
    override fun checkTarget(declaration: FirNamedFunction) {
        val symbol = declaration.symbol

        if (symbol.receiverParameterSymbol != null || symbol.contextParameterSymbols.isNotEmpty()) {
            reporter.reportOn(declaration.source, SweetDiagnostics.PROVIDES_FUNCTION_RECEIVER)
        }
        if (symbol.resolvedStatus.isSuspend) {
            reporter.reportOn(declaration.source, SweetDiagnostics.PROVIDES_FUNCTION_SUSPEND)
        }
        if (symbol.valueParameterSymbols.any { !it.hasDefaultValue }) {
            reporter.reportOn(declaration.source, SweetDiagnostics.PROVIDES_FUNCTION_PARAMETERS)
        }

        val container = symbol.getContainingClassSymbol()
        val allowedContainer = container == null || (container is FirClassSymbol<*> && container.classKind == ClassKind.OBJECT)
        if (!allowedContainer) {
            reporter.reportOn(declaration.source, SweetDiagnostics.PROVIDES_FUNCTION_NOT_TOP_LEVEL_OR_OBJECT)
        }
    }

    override fun providedClassSymbol(declaration: FirNamedFunction, session: FirSession): FirClassSymbol<*>? =
        declaration.symbol.resolvedReturnType.serviceClassSymbol(session)

    override fun providedTypeIsTypeParameter(declaration: FirNamedFunction): Boolean =
        declaration.symbol.resolvedReturnType is ConeTypeParameterType
}

internal object ProvidesPropertyChecker : ProvidesChecker<FirProperty>() {

    context(context: CheckerContext, reporter: DiagnosticReporter)
    override fun checkTarget(declaration: FirProperty) {
        val symbol = declaration.symbol

        if (symbol.isVar) {
            reporter.reportOn(declaration.source, SweetDiagnostics.PROVIDES_PROPERTY_MUTABLE)
        }
        if (symbol.receiverParameterSymbol != null || symbol.contextParameterSymbols.isNotEmpty()) {
            reporter.reportOn(declaration.source, SweetDiagnostics.PROVIDES_PROPERTY_RECEIVER)
        }

        val container = symbol.getContainingClassSymbol()
        val allowedContainer = container == null || (container is FirClassSymbol<*> && container.classKind == ClassKind.OBJECT)
        if (!allowedContainer) {
            reporter.reportOn(declaration.source, SweetDiagnostics.PROVIDES_PROPERTY_NOT_TOP_LEVEL_OR_OBJECT)
        }
    }

    override fun providedClassSymbol(declaration: FirProperty, session: FirSession): FirClassSymbol<*>? =
        declaration.symbol.resolvedReturnType.serviceClassSymbol(session)

    // providedTypeIsTypeParameter defaults to false — properties cannot have type parameters,
    // so the type-parameter check present in the old ProvidesPropertyChecker was dead code.
}

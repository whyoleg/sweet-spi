/*
 * Copyright (c) 2026 Oleg Yukhnevich. Use of this source code is governed by the Apache 2.0 license.
 */

package dev.whyoleg.sweetspi.compiler.checkers

import dev.whyoleg.sweetspi.compiler.SweetNames
import org.jetbrains.kotlin.descriptors.Visibilities
import org.jetbrains.kotlin.descriptors.Visibility
import org.jetbrains.kotlin.fir.FirAnnotationContainer
import org.jetbrains.kotlin.fir.FirSession
import org.jetbrains.kotlin.fir.declarations.getAnnotationsByClassId
import org.jetbrains.kotlin.fir.declarations.getKClassArgument
import org.jetbrains.kotlin.fir.declarations.hasAnnotation
import org.jetbrains.kotlin.fir.expressions.FirAnnotation
import org.jetbrains.kotlin.fir.resolve.lookupSuperTypes
import org.jetbrains.kotlin.fir.resolve.toRegularClassSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirClassSymbol
import org.jetbrains.kotlin.fir.types.ConeKotlinType
import org.jetbrains.kotlin.name.ClassId

internal fun Visibility.isAllowedForSweet(): Boolean =
    this == Visibilities.Public || this == Visibilities.Internal

/** Each `@Provides(service = X::class)` annotation on this declaration, paired with the resolved service type X. */
internal fun FirAnnotationContainer.providesServiceTypes(session: FirSession): List<Pair<FirAnnotation, ConeKotlinType>> =
    getAnnotationsByClassId(SweetNames.Provides, session).mapNotNull { annotation ->
        val serviceType = annotation.getKClassArgument(SweetNames.ProvidesServiceArgument, session) ?: return@mapNotNull null
        annotation to serviceType
    }

/** The service type X resolved to its declaring class symbol, or null if unresolvable. */
internal fun ConeKotlinType.serviceClassSymbol(session: FirSession): FirClassSymbol<*>? =
    toRegularClassSymbol(session)

/** True if X (the service) is itself annotated with @Service. */
internal fun FirClassSymbol<*>.isAnnotatedAsService(session: FirSession): Boolean =
    hasAnnotation(SweetNames.Service, session)

/** True if `this` provider class is a subtype of the service identified by [serviceClassId] (compared by classifier, ignoring type arguments). */
internal fun FirClassSymbol<*>.isSweetSubclassOf(serviceClassId: ClassId, session: FirSession): Boolean {
    if (classId == serviceClassId) return true
    return lookupSuperTypes(
        symbols = listOf(this),
        lookupInterfaces = true,
        deep = true,
        useSiteSession = session,
        substituteTypes = false,
    ).any { it.lookupTag.classId == serviceClassId }
}

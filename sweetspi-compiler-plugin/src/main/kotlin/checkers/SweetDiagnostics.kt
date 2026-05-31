/*
 * Copyright (c) 2026 Oleg Yukhnevich. Use of this source code is governed by the Apache 2.0 license.
 */

package dev.whyoleg.sweetspi.compiler.checkers

import org.jetbrains.kotlin.diagnostics.KtDiagnosticFactoryToRendererMap
import org.jetbrains.kotlin.diagnostics.KtDiagnosticsContainer
import org.jetbrains.kotlin.diagnostics.error0
import org.jetbrains.kotlin.diagnostics.error1
import org.jetbrains.kotlin.diagnostics.rendering.BaseDiagnosticRendererFactory
import org.jetbrains.kotlin.diagnostics.rendering.CommonRenderers
import org.jetbrains.kotlin.psi.KtElement

internal object SweetDiagnostics : KtDiagnosticsContainer() {
    // @Service
    val SERVICE_WRONG_TARGET by error0<KtElement>()
    val SERVICE_WRONG_VISIBILITY by error0<KtElement>()

    // @Provides (shared)
    val PROVIDES_AND_SERVICE_TOGETHER by error0<KtElement>()
    val PROVIDES_SERVICE_NOT_A_SERVICE by error1<KtElement, String>()
    val PROVIDES_TYPE_MISMATCH by error1<KtElement, String>()
    val PROVIDES_WRONG_VISIBILITY by error0<KtElement>()

    // @Provides on class
    val PROVIDES_CLASS_NOT_INSTANTIABLE by error0<KtElement>()
    val PROVIDES_CLASS_INNER by error0<KtElement>()
    val PROVIDES_CLASS_LOCAL by error0<KtElement>()
    val PROVIDES_CLASS_NO_CONSTRUCTOR by error0<KtElement>()

    // @Provides on function
    val PROVIDES_FUNCTION_RECEIVER by error0<KtElement>()
    val PROVIDES_FUNCTION_SUSPEND by error0<KtElement>()
    val PROVIDES_FUNCTION_PARAMETERS by error0<KtElement>()

    // @Provides on property
    val PROVIDES_PROPERTY_MUTABLE by error0<KtElement>()
    val PROVIDES_PROPERTY_RECEIVER by error0<KtElement>()

    override fun getRendererFactory(): BaseDiagnosticRendererFactory = SweetDiagnosticRenderers
}

private object SweetDiagnosticRenderers : BaseDiagnosticRendererFactory() {
    override val MAP: KtDiagnosticFactoryToRendererMap by KtDiagnosticFactoryToRendererMap("SweetSPI") { map ->
        map.put(SweetDiagnostics.SERVICE_WRONG_TARGET, "@Service is only applicable to interfaces or abstract classes")
        map.put(SweetDiagnostics.SERVICE_WRONG_VISIBILITY, "@Service declaration must be public or internal")

        map.put(SweetDiagnostics.PROVIDES_AND_SERVICE_TOGETHER, "declaration cannot be annotated with both @Service and @Provides")
        map.put(SweetDiagnostics.PROVIDES_SERVICE_NOT_A_SERVICE, "''{0}'' specified in @Provides is not annotated with @Service", CommonRenderers.STRING)
        map.put(SweetDiagnostics.PROVIDES_TYPE_MISMATCH, "provided type is not a subtype of service ''{0}''", CommonRenderers.STRING)
        map.put(SweetDiagnostics.PROVIDES_WRONG_VISIBILITY, "@Provides declaration must be public or internal")

        map.put(SweetDiagnostics.PROVIDES_CLASS_NOT_INSTANTIABLE, "@Provides class must be an object or an instantiable class (not abstract/sealed/enum/annotation/interface)")
        map.put(SweetDiagnostics.PROVIDES_CLASS_INNER, "@Provides class must not be inner")
        map.put(SweetDiagnostics.PROVIDES_CLASS_LOCAL, "@Provides class must not be local")
        map.put(SweetDiagnostics.PROVIDES_CLASS_NO_CONSTRUCTOR, "@Provides class must have a public or internal constructor with no parameters or all-default parameters")

        map.put(SweetDiagnostics.PROVIDES_FUNCTION_RECEIVER, "@Provides function must not have an extension receiver or context parameters")
        map.put(SweetDiagnostics.PROVIDES_FUNCTION_SUSPEND, "@Provides function must not be suspend")
        map.put(SweetDiagnostics.PROVIDES_FUNCTION_PARAMETERS, "@Provides function parameters must all have default values")

        map.put(SweetDiagnostics.PROVIDES_PROPERTY_MUTABLE, "@Provides property must be a val")
        map.put(SweetDiagnostics.PROVIDES_PROPERTY_RECEIVER, "@Provides property must not have an extension receiver or context parameters")
    }
}

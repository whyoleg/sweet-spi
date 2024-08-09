/*
 * Copyright (c) 2024 Oleg Yukhnevich. Use of this source code is governed by the Apache 2.0 license.
 */

package dev.whyoleg.sweetspi.processor

import com.google.devtools.ksp.*
import com.google.devtools.ksp.processing.*
import com.google.devtools.ksp.symbol.*

private const val SERVICE_NAME = "dev.whyoleg.sweetspi.Service"
private const val SERVICE_PROVIDER_NAME = "dev.whyoleg.sweetspi.ServiceProvider"

fun analyze(logger: KSPLogger, resolver: Resolver): SweetContext? {
    var isValid = true

    val serviceAnnotationType = resolver.getClassDeclarationByName(SERVICE_NAME)
        ?.asType(emptyList())
        ?: error("Failed to find '$SERVICE_NAME' type. No 'sweetspi-runtime' in classpath?")

    val serviceProviderAnnotationType = resolver.getClassDeclarationByName(SERVICE_PROVIDER_NAME)
        ?.asType(emptyList())
        ?: error("Failed to find '$SERVICE_PROVIDER_NAME' type. No 'sweetspi-runtime' in classpath?")

    // the annotation target is CLASS
    val services = resolver.getSymbolsWithAnnotation(SERVICE_NAME)
        .filterIsInstance<KSClassDeclaration>()
        .toList()
        .onEach {
            // TODO: check visibility
            if (!it.isAbstract()) {
                isValid = false
                logger.error("@Service target '${it.simpleName.asString()}' should be an 'interface' or an 'abstract class'", it)
            }
        }

    // the annotation target is CLASS, FUNCTION, PROPERTY
    val serviceProviders = resolver.getSymbolsWithAnnotation(SERVICE_PROVIDER_NAME)
        .filterIsInstance<KSDeclaration>()
        .toList()
        .onEach {
            // TODO: check visibility
            when (it) {
                is KSClassDeclaration    -> {
                    if (it.classKind != ClassKind.OBJECT) {
                        isValid = false
                        logger.error("@ServiceProvider target '${it.simpleName.asString()}' should be an 'object'", it)
                    }
                }
                is KSFunctionDeclaration -> {
                    val validFunction = it.functionKind == FunctionKind.TOP_LEVEL
                            && it.extensionReceiver == null
                            && Modifier.SUSPEND !in it.modifiers
                            && it.parameters.isEmpty()

                    if (!validFunction) {
                        isValid = false
                        logger.error(
                            "@ServiceProvider target '${it.simpleName.asString()}' should be top-level non-suspend function without receiver",
                            it
                        )
                    }
                }
                is KSPropertyDeclaration -> {
                    val validProperty = !it.isMutable
                            && it.extensionReceiver == null
                            && Modifier.SUSPEND !in it.modifiers // TODO: recheck

                    if (!validProperty) {
                        isValid = false
                        logger.error(
                            "@ServiceProvider target '${it.simpleName.asString()}' should be top-level immutable property without receiver",
                            it
                        )
                    }
                }
            }
        }

    // nothing to do here
    if (services.isEmpty() && serviceProviders.isEmpty()) return null

    // if something is not valid - no reason no
    if (!isValid) return null

    fun KSDeclaration.serviceProviderType(): KSType = when (this) {
        is KSClassDeclaration    -> asType(emptyList())
        is KSFunctionDeclaration -> returnType!!.resolve()
        is KSPropertyDeclaration -> type.resolve()
        // should be handled during validation of service providers
        else                     -> error("should not happen")
    }

    fun KSType.isInstanceOf(type: KSType): Boolean = type.isAssignableFrom(this)
    fun KSAnnotation.isInstanceOf(type: KSType): Boolean = annotationType.resolve().isInstanceOf(type)

    fun KSDeclaration.declaredServiceTypes(): Set<KSType>? {

        val serviceProviderAnnotation = annotations.find { it.isInstanceOf(serviceProviderAnnotationType) }
            ?: error("Can't find '@ServiceProvider' annotation for '$simpleName' while this symbol was resolved by this annotation")

        @Suppress("UNCHECKED_CAST")
        val serviceTypes = serviceProviderAnnotation.arguments.find { it.name?.asString() == "services" }?.value as? List<KSType>
            ?: error("Can't find 'services' argument for '@ServiceProvider' annotation on '$simpleName'")

        if (serviceTypes.isEmpty()) return null

        val serviceProviderType = serviceProviderType()

        serviceTypes.forEach { serviceType ->
            if (serviceType.declaration.annotations.none { it.isInstanceOf(serviceAnnotationType) }) {
                isValid = false
                logger.error("'$serviceType' doesn't have '$SERVICE_NAME' annotation", this)
            }

            if (!serviceProviderType.isInstanceOf(serviceType)) {
                isValid = false
                logger.error("'$serviceProviderType' doesn't inherit '$serviceType'", this)
            }
        }

        return serviceTypes.toSet()
    }

    fun KSDeclaration.resolvedServiceTypes(): Set<KSType> {
        fun KSType.collectServiceTypes(builder: MutableSet<KSType>) {
            val cls = declaration as KSClassDeclaration
            if (cls.annotations.any { it.isInstanceOf(serviceAnnotationType) }) builder.add(this)
            cls.superTypes.forEach { it.resolve().collectServiceTypes(builder) }
        }

        return buildSet {
            serviceProviderType().collectServiceTypes(this)
        }
    }

    val serviceProvidersMap: Map<KSType, List<KSDeclaration>> = buildMap<KSType, MutableList<KSDeclaration>> {
        serviceProviders.forEach { serviceProvider ->
            val serviceTypes = serviceProvider.declaredServiceTypes() ?: serviceProvider.resolvedServiceTypes()
            if (serviceTypes.isEmpty()) {
                isValid = false
                // TODO: warn or error?
                logger.error("No applicable services found for '${serviceProvider.simpleName.asString()}'", serviceProvider)
                return@forEach
            }
            serviceTypes.forEach { serviceType ->
                getOrPut(serviceType, ::mutableListOf).add(serviceProvider)
            }
        }
    }

    if (!isValid) return null

    val packageName = (services + serviceProviders).asSequence()
        .map { it.packageName.asString() }
        .distinct()
        .map { it.split(".").asSequence() }
        .reduce { common, other ->
            common.zip(other).takeWhile { it.first == it.second }.map { it.first }
        }.joinToString(".")

    val dependencies = Dependencies(
        aggregating = true,
        *(services + serviceProviders).mapNotNull(KSDeclaration::containingFile).distinct().toTypedArray()
    )

    return SweetContext(
        packageName = packageName,
        dependencies = dependencies,
        services = services,
        serviceProviders = serviceProvidersMap
    )
}


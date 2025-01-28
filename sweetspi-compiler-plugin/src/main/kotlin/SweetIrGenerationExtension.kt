/*
 * Copyright (c) 2025 Oleg Yukhnevich. Use of this source code is governed by the Apache 2.0 license.
 */

package dev.whyoleg.sweetspi.compiler

import org.jetbrains.kotlin.backend.common.extensions.*
import org.jetbrains.kotlin.backend.common.lower.*
import org.jetbrains.kotlin.descriptors.*
import org.jetbrains.kotlin.ir.*
import org.jetbrains.kotlin.ir.builders.*
import org.jetbrains.kotlin.ir.builders.declarations.*
import org.jetbrains.kotlin.ir.declarations.*
import org.jetbrains.kotlin.ir.expressions.*
import org.jetbrains.kotlin.ir.expressions.impl.*
import org.jetbrains.kotlin.ir.symbols.*
import org.jetbrains.kotlin.ir.types.*
import org.jetbrains.kotlin.ir.util.*
import org.jetbrains.kotlin.name.*
import org.jetbrains.kotlin.platform.jvm.*
import java.nio.file.*
import kotlin.io.path.*

private val SweetOrigin: IrDeclarationOrigin = IrDeclarationOriginImpl("SWEET_SPI")

// here should be:
// - find all @Service/@ServiceProvider/@JvmService/@JvmServiceProvider on class-likes
// - for JVM:
//    - (step1) for `@Service` - generate an additional interface (@PublishedApi internal) (FIR + IR)
//    - (step1) for `@ServiceProvider` - generate an additional interface impl and meta-inf (FIR + IR + RESOURCES)
//    - (step2) for `ServiceLoader.load` - intrinsic for R8 optimizable (IR)
//    - (step3) for `@JvmService` - do nothing
//    - (step3) for `@JvmServiceProvider` - generate meta-inf (RESOURCES)
// -    (step1) for klib - generate init with `@EagerInitializer` (FIR + IR)

// klib:
// - if annotated -> generate call
// jvm:
// - if annotated -> generate a lot of different things :)

@OptIn(UnsafeDuringIrConstructionAPI::class)
class SweetIrGenerationExtension(
    private val logger: IrMessageLogger,
    private val resourcesPath: Path,
) : IrGenerationExtension {

    override fun generate(moduleFragment: IrModuleFragment, pluginContext: IrPluginContext) {
        if (!pluginContext.platform.isJvm()) return
        // generate based on service/serviceProvider based on platform

        // TODO: lazy, needed for jvm only
        //  create custom pluginContext?
        val publishedApiAnnotation by lazy {
            pluginContext.referenceConstructors(StandardClassIds.Annotations.PublishedApi).single()
        }

        // handle @Service
        val services = moduleFragment.files.flatMap { file ->
            file.declarations.mapNotNull { declaration ->
                if (declaration is IrClass && declaration.hasAnnotation(SweetClassIds.Service)) {
                    buildJvmServiceProviderClass(pluginContext, declaration, publishedApiAnnotation)
                } else null
            }.onEach(file::addChild)
        }.associateBy(IrClass::classIdOrFail) // should be called ONLY after `addChild`

        // handle @ServiceProvider
        val serviceProviders = moduleFragment.files.flatMap { file ->
            file.declarations.flatMap { declaration ->
                val serviceTypes = findDeclaredServiceTypes(declaration)
                    ?.ifEmpty { resolveServiceTypes(declaration) }
                    ?: return@flatMap emptyList()

                // will always be ok - TBD
                declaration as IrDeclarationWithName

                // TODO: add checkers for invalid combinations
                buildJvmServiceProviderImplClasses(pluginContext, services, serviceTypes, declaration)
            }.onEach(file::addChild)
        }.groupBy { it.superTypes.single() }

        // drop all resources first
        resourcesPath.toFile().deleteRecursively()
        serviceProviders.forEach { (superType, providers) ->
            resourcesPath.resolve(
                "META-INF/services/${superType.classOrFail.owner.kotlinFqName.asString()}"
            ).createParentDirectories().writeText(
                providers.joinToString("\n") { it.kotlinFqName.asString() }
            )
        }
    }

    private fun buildJvmServiceProviderClass(
        pluginContext: IrPluginContext,
        declaration: IrClass,
        publishedApiAnnotation: IrConstructorSymbol,
    ): IrClass = pluginContext.irFactory.buildClass {
        origin = SweetOrigin
        kind = ClassKind.INTERFACE
        modality = Modality.ABSTRACT
        visibility = DescriptorVisibilities.INTERNAL
        name = Name.identifier("${declaration.name.identifier}_Provider")
    }.apply {
        createParameterDeclarations()
        superTypes += pluginContext.irBuiltIns.functionN(0).typeWith(declaration.defaultType)
        annotations += IrConstructorCallImpl.fromSymbolOwner(publishedApiAnnotation.owner.returnType, publishedApiAnnotation)
    }

    private fun buildJvmServiceProviderImplClasses(
        pluginContext: IrPluginContext,
        services: Map<ClassId, IrClass>,
        serviceTypes: List<IrType>,
        declaration: IrDeclarationWithName,
    ): List<IrClass> = serviceTypes.map { serviceType ->
        pluginContext.irFactory.buildClass {
            origin = SweetOrigin
            kind = ClassKind.CLASS
            visibility = DescriptorVisibilities.INTERNAL
            name = Name.identifier("${declaration.name.identifier}_Provider")
        }.apply {
            createParameterDeclarations()
            val serviceId = ClassId.topLevel(FqName(serviceType.classFqName!!.asString() + "_Provider"))
            superTypes += (services[serviceId]?.symbol ?: pluginContext.referenceClass(serviceId)
            ?: error("TBD: $serviceId")).defaultType
            // empty constructor
            addConstructor { isPrimary = true }.apply {
                val constructor = pluginContext.irBuiltIns.anyClass.owner.constructors.single()
                body = DeclarationIrBuilder(pluginContext, symbol).irBlockBody(startOffset, endOffset) {
                    +irDelegatingConstructorCall(constructor)
                }
            }
            addFunction {
                name = Name.identifier("invoke")
                returnType = pluginContext.irBuiltIns.anyClass.defaultType // any because of generic
            }.apply {
                dispatchReceiverParameter = parentAsClass.thisReceiver!!.copyTo(this)
                body = DeclarationIrBuilder(pluginContext, symbol).irBlockBody(startOffset, endOffset) {
                    +irReturn(this.serviceProviderInitializer(declaration))
                }
            }
        }
    }

    private fun IrBlockBodyBuilder.serviceProviderInitializer(declaration: IrDeclaration): IrExpression {
        return when (declaration) {
            is IrClass          -> {
                if (declaration.isObject) irGetObject(declaration.symbol)
                else irCall(declaration.constructors.single {
                    it.valueParameters.isEmpty() // TODO validate?
                })
            }
            is IrSimpleFunction -> {
                irCall(declaration) // should have no arguments
            }
            is IrProperty       -> {
                irCall(declaration.getter!!)
            }
            else                -> error("TBD: ${declaration::class.simpleName}")
        }
    }

    private fun findDeclaredServiceTypes(declaration: IrDeclaration): List<IrType>? {
        if (
            declaration !is IrClass &&
            declaration !is IrSimpleFunction &&
            declaration !is IrProperty
        ) return null

        val serviceProviderAnnotation = declaration.annotations.find {
            it.symbol.owner.parentAsClass.classId == SweetClassIds.ServiceProvider
        } ?: return null

        @Suppress("UNCHECKED_CAST")
        return ((serviceProviderAnnotation.getValueArgument(0) as IrVararg).elements as List<IrClassReference>).map { it.classType }
    }

    private fun resolveServiceTypes(declaration: IrDeclaration): List<IrType> {
        val rootType = when (declaration) {
            is IrClass          -> declaration
            is IrSimpleFunction -> declaration.returnType.classOrFail.owner
            is IrProperty       -> declaration.getter!!.returnType.classOrFail.owner
            else                -> error("TBD: ${declaration::class.simpleName}")
        }
        return (rootType.getAllSuperclasses() + rootType).filter {
            it.hasAnnotation(SweetClassIds.Service)
        }.map(IrClass::defaultType)
    }
}

@OptIn(UnsafeDuringIrConstructionAPI::class)
class KlibSweetIrGenerationExtension(
    private val logger: IrMessageLogger,
) : IrGenerationExtension {

    override fun generate(moduleFragment: IrModuleFragment, pluginContext: IrPluginContext) {
        if (pluginContext.platform.isJvm()) return

        val eagerInitAnnotation by lazy {
            pluginContext.referenceConstructors(ClassId.topLevel(FqName("kotlin.native.EagerInitialization"))).single()
        }

        val registry by lazy {
            pluginContext.referenceClass(
                ClassId.topLevel(FqName("dev.whyoleg.sweetspi.InternalServiceRegistry"))
            )!!
        }

        // handle @Service
        moduleFragment.files.forEach { file ->
            file.declarations.mapNotNull { declaration ->
                if (declaration is IrClass && declaration.hasAnnotation(SweetClassIds.Service)) {
                    pluginContext.irFactory.buildProperty {
                        origin = SweetOrigin
                        name = Name.identifier("${declaration.name.identifier}_Provider")
                        // TODO: public on js
                        visibility = DescriptorVisibilities.PRIVATE
                    }.apply {
                        parent = file // TODO?
                        annotations += IrConstructorCallImpl.fromSymbolOwner(eagerInitAnnotation.owner.returnType, eagerInitAnnotation)
                        addBackingField {
                            // TODO: dynamic on js
                            type = pluginContext.irBuiltIns.unitType
                            isFinal = true
                        }.apply {
                            initializer = DeclarationIrBuilder(pluginContext, symbol).run {
                                irExprBody(
                                    irCall(registry.getSimpleFunction("registerService")!!).apply {
                                        dispatchReceiver = irGetObject(registry)
                                        putValueArgument(0, kClassReference(pluginContext, declaration.defaultType))
                                    }
                                )
                            }
                        }
                    }
                } else null
            }.onEach(file::addChild)
        }

        // handle @ServiceProvider
        moduleFragment.files.forEach { file ->
            file.declarations.flatMap { declaration ->
                val serviceTypes = findDeclaredServiceTypes(declaration)
                    ?.ifEmpty { resolveServiceTypes(declaration) }
                    ?: return@flatMap emptyList()

                declaration as IrDeclarationWithName

                serviceTypes.map { serviceType ->
                    pluginContext.irFactory.buildProperty {
                        origin = SweetOrigin
                        name = Name.identifier("${declaration.name.identifier}_Provider")
                        // TODO: public on js
                        visibility = DescriptorVisibilities.PRIVATE
                    }.apply {
                        parent = file // TODO?
                        annotations += IrConstructorCallImpl.fromSymbolOwner(eagerInitAnnotation.owner.returnType, eagerInitAnnotation)
                        addBackingField {
                            // TODO: dynamic on js
                            type = pluginContext.irBuiltIns.unitType
                            isFinal = true
                        }.apply {
                            initializer = DeclarationIrBuilder(pluginContext, symbol).run {
                                irExprBody(
                                    irCall(registry.getSimpleFunction("registerServiceProvider")!!).apply {
                                        dispatchReceiver = irGetObject(registry)
                                        // error(symbol.owner.valueParameters[1].symbol.owner.type.classFqName.toString())
                                        putValueArgument(0, kClassReference(pluginContext, serviceType))
                                        putValueArgument(
                                            1,
                                            IrFunctionExpressionImpl(
                                                UNDEFINED_OFFSET, UNDEFINED_OFFSET,
                                                symbol.owner.valueParameters[1].type,
                                                pluginContext.irFactory.buildFun {
                                                    origin = IrDeclarationOrigin.LOCAL_FUNCTION_FOR_LAMBDA
                                                    name = SpecialNames.NO_NAME_PROVIDED
                                                    visibility = DescriptorVisibilities.LOCAL
                                                    returnType = serviceType
                                                    modality = Modality.FINAL
                                                }.apply {
                                                    parent = scope.getLocalDeclarationParent()
                                                    body = DeclarationIrBuilder(pluginContext, symbol).irBlockBody(startOffset, endOffset) {
                                                        +irReturn(this.serviceProviderInitializer(declaration))
                                                    }
                                                },
                                                IrStatementOrigin.LAMBDA
                                            )
                                        )
                                    }
                                )
                            }
                        }
                    }
                }
            }.onEach(file::addChild)
        }
    }

    private fun findDeclaredServiceTypes(declaration: IrDeclaration): List<IrType>? {
        val serviceProviderAnnotation = declaration.annotations.find {
            it.symbol.owner.parentAsClass.classId == SweetClassIds.ServiceProvider
        } ?: return null

        @Suppress("UNCHECKED_CAST")
        return ((serviceProviderAnnotation.getValueArgument(0) as IrVararg).elements as List<IrClassReference>).map { it.classType }
    }

    private fun IrBuilderWithScope.serviceProviderInitializer(declaration: IrDeclaration): IrExpression = when (declaration) {
        is IrClass          -> {
            if (declaration.isObject) irGetObject(declaration.symbol)
            else irCall(declaration.constructors.single {
                it.valueParameters.isEmpty() // TODO validate?
            })
        }
        is IrSimpleFunction -> {
            irCall(declaration) // should have no arguments
        }
        is IrProperty       -> {
            irCall(declaration.getter!!)
        }
        else                -> error("TBD: ${declaration::class.simpleName}")
    }

    private fun resolveServiceTypes(declaration: IrDeclaration): List<IrType> {
        val rootType = when (declaration) {
            is IrClass          -> declaration
            is IrSimpleFunction -> declaration.returnType.classOrFail.owner
            is IrProperty       -> declaration.getter!!.returnType.classOrFail.owner
            else                -> error("TBD: ${declaration::class.simpleName}")
        }
        return (rootType.getAllSuperclasses() + rootType).filter {
            it.hasAnnotation(SweetClassIds.Service)
        }.map(IrClass::defaultType)
    }

    private fun kClassReference(
        pluginContext: IrPluginContext,
        classType: IrType,
    ): IrClassReference = IrClassReferenceImpl(
        startOffset = UNDEFINED_OFFSET,
        endOffset = UNDEFINED_OFFSET,
        type = pluginContext.irBuiltIns.kClassClass.typeWith(classType),
        symbol = classType.classOrFail,
        classType = classType
    )
}

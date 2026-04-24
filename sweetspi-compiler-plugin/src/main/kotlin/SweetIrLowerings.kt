/*
 * Copyright (c) 2026 Oleg Yukhnevich. Use of this source code is governed by the Apache 2.0 license.
 */

package dev.whyoleg.sweetspi.compiler

import org.jetbrains.kotlin.backend.common.*
import org.jetbrains.kotlin.backend.common.extensions.*
import org.jetbrains.kotlin.backend.common.lower.*
import org.jetbrains.kotlin.builtins.*
import org.jetbrains.kotlin.descriptors.*
import org.jetbrains.kotlin.ir.builders.*
import org.jetbrains.kotlin.ir.builders.declarations.*
import org.jetbrains.kotlin.ir.declarations.*
import org.jetbrains.kotlin.ir.expressions.*
import org.jetbrains.kotlin.ir.expressions.impl.*
import org.jetbrains.kotlin.ir.symbols.*
import org.jetbrains.kotlin.ir.types.*
import org.jetbrains.kotlin.ir.util.*
import org.jetbrains.kotlin.name.*
import java.nio.file.*
import kotlin.io.path.*

internal val SweetOrigin: IrDeclarationOrigin = IrDeclarationOriginImpl("SWEET_SPI")//, isSynthetic = true)

private val PROVIDER_NAME = Name.identifier($$"$Provider")

class SweetIrGenerationExtension(
    private val resourcesPath: Path,
) : IrGenerationExtension {
    override fun generate(
        moduleFragment: IrModuleFragment,
        pluginContext: IrPluginContext,
    ) {
        val generatedProviders = mutableMapOf<ClassId, IrClassSymbol>()
        val generatedServices = mutableMapOf<ClassId, MutableSet<ClassId>>()
        ServiceLowering(pluginContext, generatedProviders).lower(moduleFragment)
        ServiceProviderLowering(pluginContext, generatedProviders, generatedServices).lower(moduleFragment)

        // drop all resources first
        resourcesPath.toFile().deleteRecursively()
        generatedServices.forEach { (serviceId, providerIds) ->
            resourcesPath.resolve(
                "META-INF/services/${serviceId.toJvmString()}"
            ).createParentDirectories().writeText(
                providerIds.joinToString("\n") { it.toJvmString() }
            )
        }
    }

    private fun ClassId.toJvmString(): String {
        return if (packageFqName.isRoot) {
            relativeClassName.asString().replace('.', '$')
        } else {
            buildString {
                append(packageFqName.asString())
                append(".")
                append(relativeClassName.asString().replace('.', '$'))
            }
        }
    }
}

private class ServiceLowering(
    private val context: IrPluginContext,
    private val generatedProviders: MutableMap<ClassId, IrClassSymbol>,
) : ClassLoweringPass {
    private val publishedApiClass = context.finderForBuiltins().findClass(StandardClassIds.Annotations.PublishedApi)!!
    private val serviceLoaderProviderClass = context.finderForBuiltins().findClass(SweetClassIds.ServiceLoaderProvider)!!

    @Suppress("DEPRECATION")
    override fun lower(irClass: IrClass) {
        // TODO: check flag
        if (!irClass.hasAnnotation(SweetClassIds.Service)) return

        context.irFactory.buildClass {
            origin = SweetOrigin
            kind = ClassKind.INTERFACE
            modality = Modality.ABSTRACT
            visibility = DescriptorVisibilities.INTERNAL
            name = PROVIDER_NAME
        }.apply {
            irClass.addChild(this)
            createThisReceiverParameter()

            annotateWith(publishedApiClass)
            superTypes += serviceLoaderProviderClass.typeWith(irClass.defaultType)

            // save it
            generatedProviders[classIdOrFail] = symbol
        }
    }
}

@OptIn(UnsafeDuringIrConstructionAPI::class)
private class ServiceProviderLowering(
    private val context: IrPluginContext,
    private val generatedProviders: Map<ClassId, IrClassSymbol>,
    private val generatedServices: MutableMap<ClassId, MutableSet<ClassId>>,
) : ClassLoweringPass {
    private val publishedApiClass = context.finderForBuiltins().findClass(StandardClassIds.Annotations.PublishedApi)!!
    private val anyConstructor = context.irBuiltIns.anyClass.owner.constructors.single()
    private val lazyFunctionSymbol = context.finderForBuiltins().findFunctions(
        CallableId(StandardNames.BUILT_INS_PACKAGE_FQ_NAME, Name.identifier("lazy"))
    ).single {
        it.owner.hasShape(regularParameters = 1)
    }
    private val lazyClass = context.finderForBuiltins().findClass(
        ClassId(StandardNames.BUILT_INS_PACKAGE_FQ_NAME, Name.identifier("Lazy"))
    )!!.owner
    private val lazyValueGetter = lazyClass.getPropertyGetter("value")!!
    private val lazyValueIsInitialized = lazyClass.getSimpleFunction("isInitialized")!!

    override fun lower(irClass: IrClass) {
        // TODO: check flag
        if (!irClass.hasAnnotation(SweetClassIds.ServiceProvider)) return

        val finder = context.finderForSource(irClass.file)

        serviceTypes(irClass).forEach { serviceType ->
            val serviceClassId = serviceType.classOrFail.owner.classIdOrFail
            val providerClassId = serviceClassId.createNestedClassId(PROVIDER_NAME)
            val providerClassSymbol = (generatedProviders[providerClassId] ?: finder.findClass(providerClassId))
                ?: error("Provider class ($providerClassId) not found for service type: $serviceClassId | $generatedProviders")
            val providerType = providerClassSymbol.defaultType

            context.irFactory.buildClass {
                origin = SweetOrigin
                name = Name.identifier($$"$Provider$$${serviceClassId.asFqNameString().replace('.', '_')}")
                kind = ClassKind.CLASS
                visibility = DescriptorVisibilities.INTERNAL
            }.apply {
                val xClass = this
                irClass.addChild(this)
                createThisReceiverParameter()

                annotateWith(publishedApiClass) // TODO: maybe not needed?
                superTypes += providerType

                val delegate = addField {
                    visibility = DescriptorVisibilities.PRIVATE
                    name = Name.identifier("delegate")
                    isFinal = true
                    type = lazyClass.typeWith(serviceType)
                }
                addConstructor {
                    isPrimary = true
                }.apply {
                    body = DeclarationIrBuilder(context, symbol, startOffset, endOffset).irBlockBody {
                        +irDelegatingConstructorCall(anyConstructor)

                        +irSetField(
                            receiver = irGet(thisReceiver!!),
                            field = delegate,
                            value = irCall(lazyFunctionSymbol, delegate.type).apply {
                                typeArguments[0] = serviceType
                                arguments[0] = IrFunctionExpressionImpl(
                                    startOffset = irClass.startOffset,
                                    endOffset = irClass.endOffset,
                                    type = context.irBuiltIns.functionN(0).typeWith(serviceType),
                                    function = context.irFactory.buildFun {
                                        returnType = serviceType
                                        name = Name.special("<anonymous>")
                                        visibility = DescriptorVisibilities.LOCAL
                                        origin = SweetOrigin
                                    }.apply {
                                        parent = xClass
                                        body = DeclarationIrBuilder(context, symbol, startOffset, endOffset).irBlockBody(
                                            startOffset,
                                            endOffset
                                        ) {
                                            //
//                                            private fun IrBlockBodyBuilder.serviceProviderInitializer(declaration: IrDeclaration): IrExpression {
//                                                return when (declaration) {
//                                                    is IrClass          -> {
//                                                        if (declaration.isObject) irGetObject(declaration.symbol)
//                                                        else irCall(declaration.constructors.single {
//                                                            it.parameters.isEmpty() // TODO validate?
//                                                        })
//                                                    }
//                                                    is IrSimpleFunction -> {
//                                                        irCall(declaration) // should have no arguments
//                                                    }
//                                                    is IrProperty       -> {
//                                                        irCall(declaration.getter!!)
//                                                    }
//                                                    else                -> error("TBD: ${declaration::class.simpleName}")
//                                                }
//                                            }

                                            // TODO: support not only objects
                                            +irReturn(
                                                irGetObject(irClass.symbol)
                                            )
                                        }
                                    },
                                    origin = IrStatementOrigin.LAMBDA
                                )
                            }
                        )
                    }
                }
                addProperty {
                    origin = SweetOrigin
                    name = Name.identifier("type")
                }.apply {
                    addGetter {
                        returnType = context.irBuiltIns.kClassClass.typeWith(serviceType)
                    }.apply {
                        parameters = listOf(xClass.thisReceiver!!.copyTo(this, type = xClass.defaultType))
                        body = DeclarationIrBuilder(context, symbol, startOffset, endOffset).irExprBody(
                            IrClassReferenceImpl(
                                startOffset = startOffset,
                                endOffset = endOffset,
                                type = returnType,
                                symbol = context.irBuiltIns.kClassClass,
                                classType = irClass.defaultType
                            )
                        )
                    }
                }
                addProperty {
                    origin = SweetOrigin
                    name = Name.identifier("value")
                }.apply {
                    addGetter {
                        // generics... - WTF???
                        returnType = context.irBuiltIns.anyClass.defaultType
                    }.apply {
                        parameters = listOf(xClass.thisReceiver!!.copyTo(this, type = xClass.defaultType))
                        body = DeclarationIrBuilder(context, symbol, startOffset, endOffset).irBlockBody {
                            +irReturn(irCall(lazyValueGetter, serviceType).apply {
                                arguments[0] = irGetField(irGet(parameters.single()), delegate)
                            })
                        }
                    }
                }
                addFunction {
                    origin = SweetOrigin
                    name = Name.identifier("isInitialized")
                    returnType = context.irBuiltIns.booleanType
                }.apply {
                    parameters = listOf(xClass.thisReceiver!!.copyTo(this, type = xClass.defaultType))
                    body = DeclarationIrBuilder(context, symbol, startOffset, endOffset).irBlockBody {
                        +irReturn(irCall(lazyValueIsInitialized, returnType).apply {
                            dispatchReceiver = irGetField(irGet(parameters.single()), delegate)
                        })
                    }
                }
                generatedServices.getOrPut(providerClassId, ::mutableSetOf).add(xClass.classIdOrFail)
            }
        }
    }

    private fun serviceTypes(declaration: IrDeclaration): List<IrType> {
        return findDeclaredServiceTypes(declaration).ifEmpty { resolveServiceTypes(declaration) }
    }

    private fun findDeclaredServiceTypes(declaration: IrDeclaration): List<IrType> {
        val serviceProviderAnnotation = declaration.getAnnotation(SweetClassIds.ServiceProvider.asSingleFqName())!!
        @Suppress("UNCHECKED_CAST")
        return ((serviceProviderAnnotation.arguments[0] as IrVararg).elements as List<IrClassReference>).map { it.classType }
    }

    private fun resolveServiceTypes(declaration: IrDeclaration): List<IrType> {
        val rootType = when (declaration) {
            is IrClass -> declaration
            is IrSimpleFunction -> declaration.returnType.classOrFail.owner
            is IrProperty -> declaration.getter!!.returnType.classOrFail.owner
            else -> error("TBD: ${declaration::class.simpleName}")
        }
        return (rootType.getAllSuperclasses() + rootType).filter {
            it.hasAnnotation(SweetClassIds.Service)
        }.map(IrClass::defaultType)
    }
}

@OptIn(UnsafeDuringIrConstructionAPI::class)
private fun IrMutableAnnotationContainer.annotateWith(annotationSymbol: IrClassSymbol) {
    val annotationCtor = annotationSymbol.constructors.single { it.owner.isPrimary }
    val annotationType = annotationSymbol.defaultType

    annotations += IrAnnotationImpl.fromSymbolOwner(startOffset, endOffset, annotationType, annotationCtor)
}

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

//
//@OptIn(UnsafeDuringIrConstructionAPI::class)
//class KlibSweetIrGenerationExtension(
//    private val logger: IrMessageLogger,
//) : IrGenerationExtension {
//
//    override fun generate(moduleFragment: IrModuleFragment, pluginContext: IrPluginContext) {
//        if (!pluginContext.platform.isNative()) return
//
//        val eagerInitAnnotation by lazy {
//            pluginContext.referenceConstructors(ClassId.topLevel(FqName("kotlin.native.EagerInitialization"))).single()
//        }
//
//        val registry by lazy {
//            pluginContext.referenceClass(
//                ClassId.topLevel(FqName("dev.whyoleg.sweetspi.InternalServiceRegistry"))
//            )!!
//        }
//
//        // handle @Service
//        moduleFragment.files.forEach { file ->
//            file.declarations.mapNotNull { declaration ->
//                if (declaration is IrClass && declaration.hasAnnotation(SweetClassIds.Service)) {
//                    pluginContext.irFactory.buildProperty {
//                        origin = SweetOrigin
//                        name = Name.identifier("registerService\$${declaration.name.identifier}")
//                        // TODO: public on js
//                        visibility = DescriptorVisibilities.PRIVATE
//                    }.apply {
//                        parent = file // TODO?
//                        annotations += IrConstructorCallImpl.fromSymbolOwner(eagerInitAnnotation.owner.returnType, eagerInitAnnotation)
//                        addBackingField {
//                            // TODO: dynamic on js
//                            type = pluginContext.irBuiltIns.unitType
//                            isFinal = true
//                        }.apply {
//                            initializer = DeclarationIrBuilder(pluginContext, symbol).run {
//                                irExprBody(
//                                    irCall(registry.getSimpleFunction("registerService")!!).apply {
//                                        dispatchReceiver = irGetObject(registry)
//                                        putValueArgument(0, kClassReference(pluginContext, declaration.defaultType))
//                                    }
//                                )
//                            }
//                        }
//                    }
//                } else null
//            }.onEach(file::addChild)
//        }
//
//        // handle @ServiceProvider
//        moduleFragment.files.forEach { file ->
//            file.declarations.flatMap { declaration ->
//                val serviceTypes = findDeclaredServiceTypes(declaration)
//                    ?.ifEmpty { resolveServiceTypes(declaration) }
//                    ?: return@flatMap emptyList()
//
//                declaration as IrDeclarationWithName
//
//                serviceTypes.map { serviceType ->
//                    pluginContext.irFactory.buildProperty {
//                        origin = SweetOrigin
//                        name = Name.identifier("registerServiceProvider\$${declaration.name.identifier}")
//                        // TODO: public on js
//                        visibility = DescriptorVisibilities.PRIVATE
//                    }.apply {
//                        parent = file // TODO?
//                        annotations += IrConstructorCallImpl.fromSymbolOwner(eagerInitAnnotation.owner.returnType, eagerInitAnnotation)
//                        addBackingField {
//                            // TODO: dynamic on js
//                            type = pluginContext.irBuiltIns.unitType
//                            isFinal = true
//                        }.apply {
//                            initializer = DeclarationIrBuilder(pluginContext, symbol).run {
//                                irExprBody(
//                                    irCall(registry.getSimpleFunction("registerServiceProvider")!!).apply {
//                                        dispatchReceiver = irGetObject(registry)
//                                        // error(symbol.owner.valueParameters[1].symbol.owner.type.classFqName.toString())
//                                        putValueArgument(0, kClassReference(pluginContext, serviceType))
//                                        putValueArgument(
//                                            1,
//                                            IrFunctionExpressionImpl(
//                                                UNDEFINED_OFFSET, UNDEFINED_OFFSET,
//                                                symbol.owner.valueParameters[1].type,
//                                                pluginContext.irFactory.buildFun {
//                                                    origin = IrDeclarationOrigin.LOCAL_FUNCTION_FOR_LAMBDA
//                                                    name = SpecialNames.NO_NAME_PROVIDED
//                                                    visibility = DescriptorVisibilities.LOCAL
//                                                    returnType = serviceType
//                                                    modality = Modality.FINAL
//                                                }.apply {
//                                                    parent = scope.getLocalDeclarationParent()
//                                                    body = DeclarationIrBuilder(pluginContext, symbol).irBlockBody(startOffset, endOffset) {
//                                                        +irReturn(this.serviceProviderInitializer(declaration))
//                                                    }
//                                                },
//                                                IrStatementOrigin.LAMBDA
//                                            )
//                                        )
//                                    }
//                                )
//                            }
//                        }
//                    }
//                }
//            }.onEach(file::addChild)
//        }
//    }
//
//    private fun findDeclaredServiceTypes(declaration: IrDeclaration): List<IrType>? {
//        val serviceProviderAnnotation = declaration.annotations.find {
//            it.symbol.owner.parentAsClass.classId == SweetClassIds.ServiceProvider
//        } ?: return null
//
//        @Suppress("UNCHECKED_CAST")
//        return ((serviceProviderAnnotation.getValueArgument(0) as IrVararg).elements as List<IrClassReference>).map { it.classType }
//    }
//
//    private fun IrBuilderWithScope.serviceProviderInitializer(declaration: IrDeclaration): IrExpression = when (declaration) {
//        is IrClass          -> {
//            if (declaration.isObject) irGetObject(declaration.symbol)
//            else irCall(declaration.constructors.single {
//                it.valueParameters.isEmpty() // TODO validate?
//            })
//        }
//        is IrSimpleFunction -> {
//            irCall(declaration) // should have no arguments
//        }
//        is IrProperty       -> {
//            irCall(declaration.getter!!)
//        }
//        else                -> error("TBD: ${declaration::class.simpleName}")
//    }
//
//    private fun resolveServiceTypes(declaration: IrDeclaration): List<IrType> {
//        val rootType = when (declaration) {
//            is IrClass          -> declaration
//            is IrSimpleFunction -> declaration.returnType.classOrFail.owner
//            is IrProperty       -> declaration.getter!!.returnType.classOrFail.owner
//            else                -> error("TBD: ${declaration::class.simpleName}")
//        }
//        return (rootType.getAllSuperclasses() + rootType).filter {
//            it.hasAnnotation(SweetClassIds.Service)
//        }.map(IrClass::defaultType)
//    }
//
//    private fun kClassReference(
//        pluginContext: IrPluginContext,
//        classType: IrType,
//    ): IrClassReference = IrClassReferenceImpl(
//        startOffset = UNDEFINED_OFFSET,
//        endOffset = UNDEFINED_OFFSET,
//        type = pluginContext.irBuiltIns.kClassClass.typeWith(classType),
//        symbol = classType.classOrFail,
//        classType = classType
//    )
//}

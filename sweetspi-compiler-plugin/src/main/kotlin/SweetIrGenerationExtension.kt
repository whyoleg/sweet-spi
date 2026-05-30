/*
 * Copyright (c) 2026 Oleg Yukhnevich. Use of this source code is governed by the Apache 2.0 license.
 */

package dev.whyoleg.sweetspi.compiler

import org.jetbrains.kotlin.backend.common.*
import org.jetbrains.kotlin.backend.common.extensions.*
import org.jetbrains.kotlin.backend.common.lower.*
import org.jetbrains.kotlin.descriptors.*
import org.jetbrains.kotlin.ir.builders.*
import org.jetbrains.kotlin.ir.builders.declarations.*
import org.jetbrains.kotlin.ir.declarations.*
import org.jetbrains.kotlin.ir.expressions.*
import org.jetbrains.kotlin.ir.expressions.impl.*
import org.jetbrains.kotlin.ir.symbols.*
import org.jetbrains.kotlin.ir.types.*
import org.jetbrains.kotlin.ir.util.*
import org.jetbrains.kotlin.ir.visitors.*
import org.jetbrains.kotlin.name.*
import org.jetbrains.kotlin.platform.jvm.*
import java.nio.file.*
import kotlin.io.path.*

/**
 * for each `@Service X` -> could be done in FIR - public ABI (not really a public API)
 * - (common): we need to generate `X$ServiceProvider` - nested class
 * - (klib):   we need to generate `X$ServiceRegistry` - nested class
 * TODO: is it possible to generate registry only in klib backends with new KMP compilation scheme?
 * It's easier to just do it in IR, users should not care about it
 *
 * for each `@Provides X_Impl: X` -> can't be done in FIR because we can't resolve annotation parameters eagerly to generate multiple providers - not a public API/ABI
 * - (common): we need to generate `X_Impl$X$ServiceProvider` - nested class
 * - (jvm):    we need to generate `META-INF/services/X` with `X_Impl` - external
 * - (klib):   we need to generate `@EagerInitialization private val `X_Impl$X$initializer` = X$ServiceRegistry.register(::X_Impl$X$Provider)` - same file
 *
 *  - on JVM: for each we need to generate
 */
class SweetIrGenerationExtension(
    private val resourcesPath: Path?,
    private val moduleInfoPropertiesPath: Path?,
) : IrGenerationExtension {
    override fun generate(moduleFragment: IrModuleFragment, pluginContext: IrPluginContext): Unit = when {
        pluginContext.platform.isJvm() -> generateJvm(moduleFragment, JvmSweetIrPluginContext(pluginContext))
        else                           -> generateKlib(moduleFragment, KlibSweetIrPluginContext(pluginContext))
    }

    private fun generateKlib(moduleFragment: IrModuleFragment, pluginContext: KlibSweetIrPluginContext) {
        // @Service
        ServiceProviderGenerator.generate(pluginContext, moduleFragment)

        // @Provides
        ServiceProviderImplementationGenerator.generate(pluginContext, moduleFragment)
    }

    private fun generateJvm(moduleFragment: IrModuleFragment, pluginContext: JvmSweetIrPluginContext) {
        requireNotNull(resourcesPath) { "resourcesPath cannot be null" }
        requireNotNull(moduleInfoPropertiesPath) { "moduleInfoPropertiesPath cannot be null" }

        ServiceProviderGenerator.generate(pluginContext, moduleFragment)
        val generatedProviderImplementations = ServiceProviderImplementationGenerator.generate(pluginContext, moduleFragment)
            .map { (serviceId, serviceImplIds) -> serviceId.toJvmString() to serviceImplIds.map { it.toJvmString() } }
            .toMap()

        val collectedPackages = PackagesCollector.collectPackages(moduleFragment)
        val collectedUsages = ServiceLoaderUsagesCollector.collectUsages(moduleFragment).map { it.toJvmString() }

        generateJvmServices(
            resourcesPath = resourcesPath,
            generatedProviderImplementations = generatedProviderImplementations
        )
        generateModuleInfoProperties(
            moduleInfoPropertiesPath = moduleInfoPropertiesPath,
            collectedPackages = collectedPackages,
            collectedServiceLoaderUsages = collectedUsages,
            generatedProviderImplementations = generatedProviderImplementations
        )
    }

    private fun generateJvmServices(resourcesPath: Path, generatedProviderImplementations: Map<String, List<String>>) {
        // recreate all resources
        resourcesPath.toFile().deleteRecursively()
        generatedProviderImplementations.forEach { (serviceId, serviceImplIds) ->
            resourcesPath.resolve("META-INF/services/${serviceId}").createParentDirectories().writeText(
                serviceImplIds.joinToString("\n")
            )
        }
    }

    // collect: exports, uses, provides
    // # exports
    // org.example.y
    // org.example.y.core
    // org.example.y.internal
    // # uses
    // org.example.y.Service - e.g. check for calls to kotlin and java `ServiceLoader` methods
    // # provides
    // org.example.y.Service=org.example.y.ServiceImpl
    // org.example.y.Service=org.example.y.ServiceImpl2
    // org.example.y.Service=org.example.y.ServiceImpl3
    private fun generateModuleInfoProperties(
        moduleInfoPropertiesPath: Path,
        collectedPackages: List<String>,
        collectedServiceLoaderUsages: List<String>,
        generatedProviderImplementations: Map<String, List<String>>,
    ) {
        val content = buildString {
            appendLine("# exports")
            collectedPackages.forEach(::appendLine)
            appendLine("# uses")
            collectedServiceLoaderUsages.forEach(::appendLine)
            appendLine("# provides")
            generatedProviderImplementations.forEach { (serviceId, serviceImplIds) ->
                serviceImplIds.forEach {
                    append(serviceId).append('=').appendLine(it)
                }
            }
            appendLine()
        }

        moduleInfoPropertiesPath.writeText(content)
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

private class PackagesCollector private constructor(private val collectedPackages: MutableSet<String>) : FileLoweringPass {
    override fun lower(irFile: IrFile) {
        collectedPackages.add(irFile.packageFqName.asString())
    }

    companion object {
        fun collectPackages(moduleFragment: IrModuleFragment): List<String> {
            val collectedPackages = mutableSetOf<String>()
            PackagesCollector(collectedPackages).lower(moduleFragment)
            return collectedPackages.toList()
        }
    }
}

// TODO: we might not need `BodyLoweringPass` at all really
private class ServiceLoaderUsagesCollector private constructor(private val collectedUsages: MutableSet<ClassId>) : BodyLoweringPass,
    IrVisitorVoid() {

    override fun visitCall(expression: IrCall) {
        // TODO: depending on the callableId we need to get parameter differently
        // TODO: will it detect java declarations???
        if (expression.symbol.owner.callableId in SweetNames.AllServiceLoaderCalls) {
            // TODO: get classId from parameter
            // collectedUsages.add(expression.symbol.owner.classId)
        }
        super.visitCall(expression)
    }

    override fun lower(irBody: IrBody, container: IrDeclaration) {
        irBody.accept(this, null)
    }

    companion object {
        fun collectUsages(moduleFragment: IrModuleFragment): Set<ClassId> {
            val collectedUsages = mutableSetOf<ClassId>()
            //ServiceLoaderUsagesCollector(collectedUsages).lower(moduleFragment)
            return collectedUsages
        }
    }
}

private class ServiceProviderGenerator private constructor(
    private val pluginContext: SweetIrPluginContext,
) : ClassLoweringPass {
    override fun lower(irClass: IrClass) {
        if (!irClass.hasAnnotation(SweetNames.Service)) return

        pluginContext.irFactory.buildClass {
            origin = SweetOrigin
            kind = ClassKind.INTERFACE
            modality = Modality.ABSTRACT
            visibility = DescriptorVisibilities.INTERNAL
            name = SweetNames.ServiceProviderName
        }.apply {
            irClass.addChild(this)
            createThisReceiverParameter()

            annotateWith(pluginContext.publishedApiClass)
            superTypes += pluginContext.serviceProviderClass.typeWith(irClass.defaultType)
        }

        if (pluginContext !is KlibSweetIrPluginContext) return

        // klib only addition - registry

        val registryClass = pluginContext.irFactory.buildClass {
            origin = SweetOrigin
            kind = ClassKind.OBJECT
            modality = Modality.FINAL
            visibility = DescriptorVisibilities.INTERNAL
            name = SweetNames.ServiceProviderRegistryName
        }.apply {
            irClass.addChild(this)
            createThisReceiverParameter()

            annotateWith(pluginContext.publishedApiClass)
            superTypes += pluginContext.serviceProviderRegistryClass.typeWith(irClass.defaultType)
        }

        registryClass.addConstructor {
            origin = SweetOrigin
            isPrimary = true
        }.apply {
            body = DeclarationIrBuilder(pluginContext, symbol).irBlockBody {
                +irDelegatingConstructorCall(pluginContext.serviceProviderRegistryConstructor.owner)
            }
        }

        irClass.annotateWith(pluginContext.serviceProviderRegistryHolderClass) {
            arguments[0] = IrClassReferenceImpl(
                startOffset = startOffset,
                endOffset = endOffset,
                type = pluginContext.irBuiltIns.kClassClass.defaultType,
                symbol = registryClass.symbol,
                classType = registryClass.defaultType
            )
        }
    }

    companion object {
        fun generate(
            pluginContext: SweetIrPluginContext,
            moduleFragment: IrModuleFragment,
        ) {
            ServiceProviderGenerator(pluginContext).lower(moduleFragment)
        }
    }
}

// TODO: all of those should be checked in FIR too
// TODO: we need to also lower functions/properties
// TODO: we should support functions/properties in object or companion objects, including nested objects
// TODO: for top-level functions/properties we could support `private` declarations too
// TODO: provider implementation as top-level (not nested) - needed for functions/properties initializers
// TODO: support JvmProvides
// TODO: JVM annotation flag handling!!!
private class ServiceProviderImplementationGenerator private constructor(
    private val pluginContext: SweetIrPluginContext,
    private val generatedProviderImplementations: MutableMap<ClassId, MutableSet<ClassId>>,
) : IrVisitorVoid() {
    private val currentFileAdditionalDeclarations = mutableListOf<IrDeclaration>()

    override fun visitFile(declaration: IrFile) {
        currentFileAdditionalDeclarations.clear()
        declaration.acceptChildrenVoid(this)
        declaration.declarations += currentFileAdditionalDeclarations
    }

    override fun visitClass(declaration: IrClass) {
        declaration.acceptChildrenVoid(this)

        process(declaration)
    }

    override fun visitProperty(declaration: IrProperty) {
        process(declaration)
    }

    override fun visitSimpleFunction(declaration: IrSimpleFunction) {
        process(declaration)
    }

    private fun process(declaration: IrDeclarationWithName) {
        // TODO: check declaration type somewhere - maybe in FIR checker?
        if (!declaration.hasAnnotation(SweetNames.Provides)) return

        val finder = pluginContext.finderForSource(declaration.file)

        val serviceTypes = declaration.annotations
            .filter { it.isAnnotationWithEqualFqName(SweetNames.Provides.asSingleFqName()) }
            .map { (it.arguments[0] as IrClassReference).classType }

        serviceTypes.forEach { serviceType ->
            val serviceClassId = serviceType.classOrFail.owner.classIdOrFail
            val serviceClassSymbol = finder.findClass(serviceClassId) ?: error("Service class ($serviceClassId) not found")
            val providerClassSymbol =
                serviceClassSymbol.owner.findDeclaration<IrClass> { it.name == SweetNames.ServiceProviderName }?.symbol
                    ?: error("Provider class not found for service type: $serviceClassId")
            val providerClassId = providerClassSymbol.owner.classIdOrFail
            val providerType = providerClassSymbol.defaultType

            val providerImplClass = buildServiceProviderImpl(declaration, serviceClassId, serviceType, providerType)

            currentFileAdditionalDeclarations.add(providerImplClass)

            when (pluginContext) {
                is JvmSweetIrPluginContext  -> {
                    // TODO: what about JVM with different enum flags in `@Service`?
                    // needed only to generate resources
                    generatedProviderImplementations.getOrPut(providerClassId, ::mutableSetOf).add(providerImplClass.classIdOrFail)
                }
                is KlibSweetIrPluginContext -> {

                    // klib only addition - registration

                    val registryClassSymbol =
                        serviceClassSymbol.owner.findDeclaration<IrClass> { it.name == SweetNames.ServiceProviderRegistryName }?.symbol
                            ?: error("Registry class not found for service type: $serviceClassId")

                    // generate: @EagerInitialization private val `$initializer$<serviceClassId>` = <Registry>.register(::<ProviderImpl>)
                    // for native, just field is enough, but for js/wasm we need to have annotated property
                    currentFileAdditionalDeclarations += buildServiceProviderImplInitializer(
                        declaration = declaration,
                        serviceClassId = serviceClassId,
                        registryClassSymbol = registryClassSymbol,
                        providerType = providerClassSymbol.defaultType,
                        providerImplClass = providerImplClass,
                    )
                }
            }
        }
    }

    private fun buildServiceProviderImpl(
        declaration: IrDeclarationWithName,
        serviceClassId: ClassId,
        serviceType: IrType,
        providerType: IrType,
    ): IrClass {
        val providerImplClass = pluginContext.irFactory.buildClass {
            origin = SweetOrigin
            name = SweetNames.serviceProviderImplementationName(serviceClassId, declaration.name)
            kind = ClassKind.CLASS
            visibility = DescriptorVisibilities.INTERNAL
        }.apply {
            parent = declaration.file
            createThisReceiverParameter()

            superTypes += providerType
        }

        val delegate = providerImplClass.addField {
            origin = SweetOrigin
            visibility = DescriptorVisibilities.PRIVATE
            name = Name.identifier("delegate")
            isFinal = true
            type = pluginContext.lazyClass.typeWith(serviceType)
        }

        val delegateLambda = pluginContext.irFactory.buildFun {
            origin = SweetOrigin
            name = Name.special("<anonymous>")
            visibility = DescriptorVisibilities.LOCAL
            returnType = serviceType
        }.apply {
            parent = providerImplClass
            body = DeclarationIrBuilder(pluginContext, symbol).irBlockBody {
                +irReturn(callServiceProvider(declaration))
            }
        }

        providerImplClass.addConstructor {
            origin = SweetOrigin
            isPrimary = true
        }.apply {
            body = DeclarationIrBuilder(pluginContext, symbol).irBlockBody {
                +irDelegatingConstructorCall(pluginContext.anyConstructor)
                +irSetField(
                    receiver = irGet(providerImplClass.thisReceiver!!),
                    field = delegate,
                    value = irCall(pluginContext.lazyFunctionSymbol, delegate.type).apply {
                        typeArguments[0] = serviceType
                        arguments[0] = IrFunctionExpressionImpl(
                            startOffset = startOffset,
                            endOffset = endOffset,
                            type = context.irBuiltIns.functionN(0).typeWith(serviceType),
                            function = delegateLambda,
                            origin = IrStatementOrigin.LAMBDA
                        )
                    }
                )
            }
        }
        // implement `ServiceProvider.type`
        providerImplClass.addProperty {
            origin = SweetOrigin
            name = Name.identifier("type")
        }.apply {
            addGetter {
                origin = SweetOrigin
                returnType = pluginContext.irBuiltIns.kClassClass.typeWith(serviceType)
            }.apply {
                parameters = listOf(providerImplClass.thisReceiver!!.copyTo(this, type = providerImplClass.defaultType))
                body = DeclarationIrBuilder(pluginContext, symbol, startOffset, endOffset).irBlockBody {
                    // in case of function/properties we use return type
                    val cls = when (declaration) {
                        is IrClass          -> declaration
                        is IrSimpleFunction -> declaration.returnType.classOrFail.owner
                        is IrProperty       -> declaration.getter!!.returnType.classOrFail.owner
                        else                -> error("Unexpected declaration type: ${declaration::class.simpleName}")
                    }
                    +irReturn(
                        IrClassReferenceImpl(
                            startOffset = startOffset,
                            endOffset = endOffset,
                            type = returnType,
                            symbol = cls.symbol,
                            classType = cls.defaultType
                        )
                    )
                }
            }
        }
        // implement `Lazy.value`
        providerImplClass.addProperty {
            origin = SweetOrigin
            name = Name.identifier("value")
        }.apply {
            addGetter {
                origin = SweetOrigin
                // generics... - WTF???
                returnType = pluginContext.irBuiltIns.anyType
            }.apply {
                parameters = listOf(providerImplClass.thisReceiver!!.copyTo(this, type = providerImplClass.defaultType))
                body = DeclarationIrBuilder(pluginContext, symbol, startOffset, endOffset).irBlockBody {
                    +irReturn(irCall(pluginContext.lazyValueGetter, serviceType).apply {
                        arguments[0] = irGetField(irGet(parameters.single()), delegate)
                    })
                }
            }
        }
        // implement `Lazy.isInitialized`
        providerImplClass.addFunction {
            origin = SweetOrigin
            name = Name.identifier("isInitialized")
            returnType = pluginContext.irBuiltIns.booleanType
        }.apply {
            parameters = listOf(providerImplClass.thisReceiver!!.copyTo(this, type = providerImplClass.defaultType))
            body = DeclarationIrBuilder(pluginContext, symbol, startOffset, endOffset).irBlockBody {
                +irReturn(irCall(pluginContext.lazyValueIsInitialized, returnType).apply {
                    arguments[0] = irGetField(irGet(parameters.single()), delegate)
                })
            }
        }

        return providerImplClass
    }

    private fun buildServiceProviderImplInitializer(
        declaration: IrDeclarationWithName,
        serviceClassId: ClassId,
        registryClassSymbol: IrClassSymbol,
        providerType: IrType,
        providerImplClass: IrClass,
    ): IrProperty {
        pluginContext as KlibSweetIrPluginContext

        return pluginContext.irFactory.buildProperty {
            origin = SweetOrigin
            visibility = DescriptorVisibilities.PRIVATE
            name = SweetNames.serviceProviderInitializerName(serviceClassId, declaration.name)
        }.apply {
            parent = declaration.file
            annotateWith(pluginContext.eagerInitializationClass)
            addBackingField {
                isStatic = true // without it - it doesn't work on js/wasm
                isFinal = true
                type = pluginContext.irBuiltIns.unitType
            }.apply {
                initializer = DeclarationIrBuilder(pluginContext, symbol, startOffset, endOffset).run {
                    irExprBody(
                        irCall(pluginContext.serviceProviderRegistryRegister, context.irBuiltIns.unitType).apply {
                            arguments[0] = irGetObject(registryClassSymbol)
                            arguments[1] = IrFunctionReferenceImpl.fromSymbolOwner(
                                startOffset = startOffset,
                                endOffset = endOffset,
                                type = context.irBuiltIns.functionN(0).typeWith(providerType),
                                symbol = providerImplClass.constructors.single().symbol,
                                typeArgumentsCount = 0,
                                reflectionTarget = null,
                                origin = IrStatementOrigin.LAMBDA
                            )
                        }
                    )
                }
            }
        }
    }

    private fun IrBuilderWithScope.callServiceProvider(declaration: IrDeclaration): IrExpression = when (declaration) {
        is IrClass if declaration.isObject -> irGetObject(declaration.symbol)
        is IrClass                         -> {
            val visibleConstructors = declaration.constructors.filter {
                // take only visible constructors
                it.visibility == DescriptorVisibilities.PUBLIC || it.visibility == DescriptorVisibilities.INTERNAL
            }.toList()
            // take only constructors with no parameters (first) or all default ones (fallback)
            val constructor = visibleConstructors.firstOrNull {
                it.parameters.isEmpty()
            } ?: visibleConstructors.firstOrNull {
                it.parameters.all { it.defaultValue != null }
            } ?: error("No suitable constructor found for ${declaration.name}") // should be handled in FIR

            irCall(constructor)
        }
        is IrSimpleFunction                -> {
            require(
                declaration.parameters.isEmpty() ||
                        declaration.parameters.all { it.defaultValue != null }
            ) { "Function ${declaration.name} must have default values for all parameters" }

            irCall(declaration)
        }
        is IrProperty                      -> {
            requireNotNull(declaration.getter) { "Property ${declaration.name} must have a getter" }
            require(declaration.getter!!.parameters.isEmpty()) { "Property ${declaration.name} must have no parameters" }

            irCall(declaration.getter!!)
        }
        else                               -> error("Unexpected declaration type: ${declaration::class.simpleName}")
    }

    companion object {
        fun generate(
            pluginContext: SweetIrPluginContext,
            moduleFragment: IrModuleFragment,
        ): Map<ClassId, Set<ClassId>> {
            val generatedProviderImplementations = mutableMapOf<ClassId, MutableSet<ClassId>>()
            moduleFragment.acceptChildrenVoid(ServiceProviderImplementationGenerator(pluginContext, generatedProviderImplementations))
            return generatedProviderImplementations
        }
    }
}

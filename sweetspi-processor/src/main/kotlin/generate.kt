/*
 * Copyright (c) 2024 Oleg Yukhnevich. Use of this source code is governed by the Apache 2.0 license.
 */

package dev.whyoleg.sweetspi.processor

import com.google.devtools.ksp.processing.*
import com.google.devtools.ksp.symbol.*
import com.squareup.kotlinpoet.*
import com.squareup.kotlinpoet.KModifier.*
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.ksp.*

private val KCLASS = ClassName("kotlin.reflect", "KClass").parameterizedBy(STAR)

fun generate(codeGenerator: CodeGenerator, platform: PlatformInfo, context: SweetContext) {
    // KSP doesn't support Wasm platform info
    val isJs = platform.toString() == "JS"
    val isWasm = platform.toString() == "Wasm"
    val isNative = platform is NativePlatformInfo
    val isJvm = platform is JvmPlatformInfo
    val moduleName = context.packageName.replace(".", "_")

    fun servicesPropertySpec(propertyName: String, serviceNames: List<ClassName>): PropertySpec =
        PropertySpec.builder(propertyName, SET.parameterizedBy(KCLASS))
            .addModifiers(OVERRIDE)
            .getter(
                when {
                    serviceNames.isEmpty() -> {
                        FunSpec.getterBuilder().addStatement("return emptySet<%T>()", NOTHING).build()
                    }
                    else                   -> {
                        val types = serviceNames.joinToString(",") { "%T::class" }
                        FunSpec.getterBuilder().addStatement("return setOf($types)", *serviceNames.toTypedArray()).build()
                    }
                }
            ).build()

    fun providersFunctionSpec(): FunSpec =
        FunSpec.builder("providers")
            .addModifiers(OVERRIDE)
            .returns(LIST.parameterizedBy(STAR))
            .addParameter("cls", KCLASS)
            .addCode(
                CodeBlock.builder().beginControlFlow("return when (cls)").apply {
                    context.serviceProviders.forEach { (service, providers) ->
                        val data = providers.map {
                            when (it) {
                                is KSClassDeclaration -> "%T" to it.toClassName()
                                is KSFunctionDeclaration -> "%M()" to MemberName(it.packageName.asString(), it.simpleName.asString())
                                is KSPropertyDeclaration -> "%M" to MemberName(it.packageName.asString(), it.simpleName.asString())
                                else -> error("should not happen")
                            }
                        }
                        val args = data.joinToString(", ") { it.first }
                        val values = data.map { it.second }.toTypedArray()
                        addStatement("%T::class -> listOf<%T>($args)", service.toClassName(), service.toClassName(), *values)
                    }
                    addStatement("else -> emptyList<%T>()", NOTHING)
                }.endControlFlow().build()
            ).build()

    fun moduleTypeSpec(): TypeSpec =
        TypeSpec.objectBuilder(moduleName)
            // for JVM we need to be able to reference it in a service file
            .addModifiers(if (isJvm) INTERNAL else PRIVATE)
            .apply {
                // hide from completion
                if (isJvm) addAnnotation(
                    AnnotationSpec.builder(Deprecated::class)
                        .addMember("%S", "")
                        .addMember("level = %T.HIDDEN", DeprecationLevel::class)
                        .build()
                )
            }
            .addSuperinterface(ClassName("dev.whyoleg.sweetspi.internal", "InternalServiceModule"))
            .addProperty(servicesPropertySpec("services", context.services.map { it.toClassName() }))
            .addProperty(servicesPropertySpec("requiredServices", context.serviceProviders.keys.map { it.toClassName() }))
            .addFunction(providersFunctionSpec())
            .addFunction(
                FunSpec.builder("toString")
                    .addModifiers(OVERRIDE)
                    .returns(String::class)
                    .addStatement("return %S", moduleName)
                    .build()
            ).build()

    fun generateInitPropertySpec(): PropertySpec =
        PropertySpec.builder(
            "init_$moduleName",
            if (isJs) DYNAMIC else UNIT,
            if (isJs) PUBLIC else PRIVATE
        ).apply {
            if (isJs) {
                addAnnotation(
                    AnnotationSpec.builder(Deprecated::class)
                        .addMember("%S", "")
                        .addMember("level = %T.HIDDEN", DeprecationLevel::class)
                        .build()
                )
                addAnnotation(ClassName("kotlin.js", "JsExport"))
                addAnnotation(ClassName("kotlin.js", "EagerInitialization"))
            }
            if (isWasm) {
                addAnnotation(ClassName("kotlin", "EagerInitialization"))
            }
            if (isNative) {
                addAnnotation(ClassName("kotlin.native", "EagerInitialization"))
            }
        }.initializer(
            "%M($moduleName)",
            MemberName("dev.whyoleg.sweetspi.internal", "registerInternalServiceModule")
        ).build()

    FileSpec.builder(context.packageName, moduleName).apply {
        addType(moduleTypeSpec())

        addAnnotation(
            AnnotationSpec.builder(ClassName("kotlin", "OptIn")).apply {
                addMember("%T::class", ClassName("dev.whyoleg.sweetspi.internal", "InternalSweetSpiApi"))
                if (!isJvm) addMember("%T::class", ClassName("kotlin", "ExperimentalStdlibApi"))
                if (isJs) addMember("%T::class", ClassName("kotlin.js", "ExperimentalJsExport"))
            }.build()
        )

        if (!isJvm) {
            addAnnotation(AnnotationSpec.builder(Suppress::class).addMember("%S", "DEPRECATION").build())
            addProperty(generateInitPropertySpec())
        }
    }.build().writeTo(codeGenerator, context.dependencies)

    if (isJvm) {
        codeGenerator.createNewFileByPath(
            dependencies = context.dependencies,
            path = "META-INF/services/dev.whyoleg.sweetspi.internal.InternalServiceModule",
            extensionName = ""
        ).use {
            it.write("${context.packageName}.$moduleName".encodeToByteArray())
        }
    }
}

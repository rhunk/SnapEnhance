package me.rhunk.snapmapper.impl

import me.rhunk.snapmapper.AbstractClassMapper
import me.rhunk.snapmapper.MapperContext
import me.rhunk.snapmapper.ext.findConstString
import me.rhunk.snapmapper.ext.getClassName
import me.rhunk.snapmapper.ext.hasStaticConstructorString
import me.rhunk.snapmapper.ext.isEnum
import java.lang.reflect.Modifier

class CompositeConfigurationProviderMapper : AbstractClassMapper() {
    override fun run(context: MapperContext) {
        for (classDef in context.classes) {
            val constructor = classDef.methods.firstOrNull { it.name == "<init>" } ?: continue
            if (constructor.parameterTypes.size == 0 || constructor.parameterTypes[0] != "Ljava/util/List;") continue
            if (constructor.implementation?.findConstString("CompositeConfigurationProvider") != true) continue

            val getPropertyMethod = classDef.methods.first { method ->
                method.parameterTypes.size > 1 &&
                method.returnType == "Ljava/lang/Object;" &&
                context.getClass(method.parameterTypes[0])?.interfaces?.contains("Ljava/io/Serializable;") == true &&
                context.getClass(method.parameterTypes[1])?.let { it.isEnum() && it.hasStaticConstructorString("BOOLEAN") } == true
            }

            val configEnumInterface = context.getClass(getPropertyMethod.parameterTypes[0])!!
            val enumType = context.getClass(getPropertyMethod.parameterTypes[1])!!

            val observePropertyMethod = classDef.methods.first {
                it.parameterTypes.size > 2 &&
                it.parameterTypes[0] == configEnumInterface.type &&
                it.parameterTypes[1] == "Ljava/lang/String;" &&
                it.parameterTypes[2] == enumType.type
            }

            val enumGetDefaultValueMethod = configEnumInterface.methods.first { context.getClass(it.returnType)?.interfaces?.contains("Ljava/io/Serializable;") == true }
            val defaultValueField = context.getClass(enumGetDefaultValueMethod.returnType)!!.fields.first {
                Modifier.isFinal(it.accessFlags) &&
                Modifier.isPublic(it.accessFlags) &&
                it.type == "Ljava/lang/Object;"
            }

            context.addMapping("CompositeConfigurationProvider",
                "class" to classDef.getClassName(),
                "observeProperty" to observePropertyMethod.name,
                "getProperty" to getPropertyMethod.name,
                "enum" to mapOf(
                    "class" to configEnumInterface.getClassName(),
                    "getValue" to enumGetDefaultValueMethod.name,
                    "defaultValueField" to defaultValueField.name
                )
            )
            return
        }
    }
}
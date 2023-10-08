package me.rhunk.snapenhance.mapper.impl

import me.rhunk.snapenhance.mapper.AbstractClassMapper
import me.rhunk.snapenhance.mapper.ext.findConstString
import me.rhunk.snapenhance.mapper.ext.getClassName
import me.rhunk.snapenhance.mapper.ext.hasStaticConstructorString
import me.rhunk.snapenhance.mapper.ext.isEnum
import java.lang.reflect.Modifier

class CompositeConfigurationProviderMapper : AbstractClassMapper() {
    init {
        mapper {
            for (classDef in classes) {
                val constructor = classDef.methods.firstOrNull { it.name == "<init>" } ?: continue
                if (constructor.parameterTypes.size == 0 || constructor.parameterTypes[0] != "Ljava/util/List;") continue
                if (constructor.implementation?.findConstString("CompositeConfigurationProvider") != true) continue

                val getPropertyMethod = classDef.methods.first { method ->
                    method.parameterTypes.size > 1 &&
                            method.returnType == "Ljava/lang/Object;" &&
                            getClass(method.parameterTypes[0])?.interfaces?.contains("Ljava/io/Serializable;") == true &&
                            getClass(method.parameterTypes[1])?.let { it.isEnum() && it.hasStaticConstructorString("BOOLEAN") } == true
                }

                val configEnumInterface = getClass(getPropertyMethod.parameterTypes[0])!!
                val enumType = getClass(getPropertyMethod.parameterTypes[1])!!

                val observePropertyMethod = classDef.methods.first {
                    it.parameterTypes.size > 2 &&
                            it.parameterTypes[0] == configEnumInterface.type &&
                            it.parameterTypes[1] == "Ljava/lang/String;" &&
                            it.parameterTypes[2] == enumType.type
                }

                val enumGetDefaultValueMethod = configEnumInterface.methods.first { getClass(it.returnType)?.interfaces?.contains("Ljava/io/Serializable;") == true }
                val defaultValueField = getClass(enumGetDefaultValueMethod.returnType)!!.fields.first {
                    Modifier.isFinal(it.accessFlags) &&
                            Modifier.isPublic(it.accessFlags) &&
                            it.type == "Ljava/lang/Object;"
                }

                addMapping("CompositeConfigurationProvider",
                    "class" to classDef.getClassName(),
                    "observeProperty" to observePropertyMethod.name,
                    "getProperty" to getPropertyMethod.name,
                    "enum" to mapOf(
                        "class" to configEnumInterface.getClassName(),
                        "getValue" to enumGetDefaultValueMethod.name,
                        "defaultValueField" to defaultValueField.name
                    )
                )
                return@mapper
            }
        }
    }
}
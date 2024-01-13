package me.rhunk.snapenhance.mapper.impl

import me.rhunk.snapenhance.mapper.AbstractClassMapper
import me.rhunk.snapenhance.mapper.ext.findConstString
import me.rhunk.snapenhance.mapper.ext.getClassName
import me.rhunk.snapenhance.mapper.ext.hasStaticConstructorString
import me.rhunk.snapenhance.mapper.ext.isEnum
import com.android.tools.smali.dexlib2.iface.instruction.formats.Instruction21c
import com.android.tools.smali.dexlib2.iface.instruction.formats.Instruction35c
import com.android.tools.smali.dexlib2.iface.reference.FieldReference
import com.android.tools.smali.dexlib2.iface.reference.MethodReference
import java.lang.reflect.Modifier

class CompositeConfigurationProviderMapper : AbstractClassMapper("CompositeConfigurationProvider") {
    val classReference = classReference("class")
    val observeProperty = string("observeProperty")
    val getProperty = string("getProperty")
    val configEnumMapping = mapOf(
        "class" to classReference("enumClass"),
        "getValue" to string("enumGetValue"),
        "getCategory" to string("enumGetCategory"),
        "defaultValueField" to string("enumDefaultValueField")
    )
    val appExperimentProvider = mapOf(
        "class" to classReference("appExperimentProviderClass"),
        "getBooleanAppExperimentClass" to classReference("getBooleanAppExperimentClass"),
        "hasExperimentMethod" to string("hasExperimentMethod")
    )

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

                val hasExperimentMethodReference = observePropertyMethod.implementation?.instructions?.firstOrNull { instruction ->
                    if (instruction !is Instruction35c) return@firstOrNull false
                    (instruction.reference as? MethodReference)?.let { methodRef ->
                        methodRef.returnType == "Z" && methodRef.parameterTypes.size == 1 && methodRef.parameterTypes[0] == configEnumInterface.type
                    } == true
                }?.let { (it as Instruction35c).reference as MethodReference }

                val getBooleanAppExperimentClass = classDef.methods.first {
                    // search for observeBoolean method
                    it.parameterTypes.size == 1 &&
                    it.parameterTypes[0] == configEnumInterface.type &&
                    it.implementation?.findConstString("observeBoolean") == true
                }.let { method ->
                    // search for static field invocation of GetBooleanAppExperiment class
                    val getBooleanAppExperimentClassFieldInstruction = method.implementation?.instructions?.firstOrNull { instruction ->
                        if (instruction !is Instruction21c) return@firstOrNull false
                        val fieldReference = instruction.reference as? FieldReference ?: return@firstOrNull false
                        getClass(fieldReference.definingClass)?.methods?.any {
                            it.returnType == "Ljava/lang/Object;" &&
                            it.parameterTypes.size == 2 &&
                            (0..1).all { i -> it.parameterTypes[i] == "Ljava/lang/Object;" }
                        } == true
                    }?.let { (it as Instruction21c).reference as FieldReference }

                    getClass(getBooleanAppExperimentClassFieldInstruction?.definingClass)?.getClassName()
                }

                val enumGetDefaultValueMethod = configEnumInterface.methods.first { getClass(it.returnType)?.interfaces?.contains("Ljava/io/Serializable;") == true }
                val enumGetCategoryMethod = configEnumInterface.methods.first { it.parameterTypes.size == 0 && getClass(it.returnType)?.isEnum() == true }
                val defaultValueField = getClass(enumGetDefaultValueMethod.returnType)!!.fields.first {
                    Modifier.isFinal(it.accessFlags) &&
                            Modifier.isPublic(it.accessFlags) &&
                            it.type == "Ljava/lang/Object;"
                }

                classReference.set(classDef.getClassName())
                observeProperty.set(observePropertyMethod.name)
                getProperty.set(getPropertyMethod.name)

                configEnumMapping["class"]?.set(configEnumInterface.getClassName())
                configEnumMapping["getValue"]?.set(enumGetDefaultValueMethod.name)
                configEnumMapping["getCategory"]?.set(enumGetCategoryMethod.name)
                configEnumMapping["defaultValueField"]?.set(defaultValueField.name)

                hasExperimentMethodReference?.let {
                    appExperimentProvider["class"]?.set(getClass(it.definingClass)?.getClassName())
                    appExperimentProvider["getBooleanAppExperimentClass"]?.set(getBooleanAppExperimentClass)
                    appExperimentProvider["hasExperimentMethod"]?.set(hasExperimentMethodReference.name)
                }

                return@mapper
            }
        }
    }
}

package me.rhunk.snapenhance.mapper.impl

import me.rhunk.snapenhance.mapper.AbstractClassMapper
import me.rhunk.snapenhance.mapper.ext.getClassName
import me.rhunk.snapenhance.mapper.ext.hasConstructorString
import me.rhunk.snapenhance.mapper.ext.hasStaticConstructorString
import me.rhunk.snapenhance.mapper.ext.isAbstract
import me.rhunk.snapenhance.mapper.ext.isEnum

class OperaPageViewControllerMapper : AbstractClassMapper("OperaPageViewController") {
    val classReference = classReference("class")
    val viewStateField = string("viewStateField")
    val layerListField = string("layerListField")
    val onDisplayStateChange = string("onDisplayStateChange")
    val onDisplayStateChangeGesture = string("onDisplayStateChangeGesture")

    init {
        mapper {
            for (clazz in classes) {
                if (!clazz.isAbstract()) continue
                if (!clazz.hasConstructorString("OperaPageViewController") || !clazz.hasStaticConstructorString("ad_product_type")) {
                    continue
                }

                val viewStateDexField = clazz.fields.first { field ->
                    val fieldClass = getClass(field.type) ?: return@first false
                    fieldClass.isEnum() && fieldClass.hasStaticConstructorString("FULLY_DISPLAYED")
                }

                val layerListDexField = clazz.fields.first { it.type == "Ljava/util/ArrayList;" }

                val onDisplayStateChangeDexMethod = clazz.methods.firstOrNull {
                    if (it.returnType != "V" || it.parameterTypes.size != 1) return@firstOrNull false
                    val firstParameterType = getClass(it.parameterTypes[0]) ?: return@firstOrNull false
                    if (firstParameterType.type == clazz.type || !firstParameterType.isAbstract()) return@firstOrNull false
                    //check if the class contains a field with the enumViewStateClass type
                    firstParameterType.fields.any { field ->
                        field.type == viewStateDexField.type
                    }
                }

                val onDisplayStateChangeGestureDexMethod = clazz.methods.first {
                    if (it.returnType != "V" || it.parameterTypes.size != 2) return@first false
                    val firstParameterType = getClass(it.parameterTypes[0]) ?: return@first false
                    val secondParameterType = getClass(it.parameterTypes[1]) ?: return@first false
                    firstParameterType.isEnum() && secondParameterType.isEnum()
                }

                classReference.set(clazz.getClassName())
                viewStateField.set(viewStateDexField.name)
                layerListField.set(layerListDexField.name)
                onDisplayStateChange.set(onDisplayStateChangeDexMethod?.name)
                onDisplayStateChangeGesture.set(onDisplayStateChangeGestureDexMethod.name)

                return@mapper
            }
        }
    }
}
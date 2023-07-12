package me.rhunk.snapmapper.impl

import me.rhunk.snapmapper.AbstractClassMapper
import me.rhunk.snapmapper.MapperContext
import me.rhunk.snapmapper.ext.hasConstructorString
import me.rhunk.snapmapper.ext.hasStaticConstructorString
import me.rhunk.snapmapper.ext.isAbstract
import me.rhunk.snapmapper.ext.isEnum

class OperaPageViewControllerMapper : AbstractClassMapper() {
    override fun run(context: MapperContext) {
        for (clazz in context.classes) {
            if (!clazz.isAbstract()) continue
            if (!clazz.hasConstructorString("OperaPageViewController") || !clazz.hasStaticConstructorString("ad_product_type")) {
                continue
            }

            val viewStateField = clazz.fields.first { field ->
                val fieldClass = context.getClass(field.type) ?: return@first false
                fieldClass.isEnum() && fieldClass.hasStaticConstructorString("FULLY_DISPLAYED")
            }

            val layerListField = clazz.fields.first { it.type == "Ljava/util/ArrayList;" }

            val onDisplayStateChange = clazz.methods.first {
                if (it.returnType != "V" || it.parameterTypes.size != 1) return@first false
                val firstParameterType = context.getClass(it.parameterTypes[0]) ?: return@first false
                //check if the class contains a field with the enumViewStateClass type
                firstParameterType.fields.any { field ->
                    field.type == viewStateField.type
                }
            }

            val onDisplayStateChangeGesture = clazz.methods.first {
                if (it.returnType != "V" || it.parameterTypes.size != 2) return@first false
                val firstParameterType = context.getClass(it.parameterTypes[0]) ?: return@first false
                val secondParameterType = context.getClass(it.parameterTypes[1]) ?: return@first false
                firstParameterType.isEnum() && secondParameterType.isEnum()
            }

            context.addMapping("OperaPageViewController",
                "class" to clazz.type.replace("L", "").replace(";", ""),
                "viewStateField" to viewStateField.name,
                "layerListField" to layerListField.name,
                "onDisplayStateChange" to onDisplayStateChange.name,
                "onDisplayStateChangeGesture" to onDisplayStateChangeGesture.name
            )

            return
        }
    }
}
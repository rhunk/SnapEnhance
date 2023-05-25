package me.rhunk.snapenhance.mapping.impl

import me.rhunk.snapenhance.mapping.Mapper
import me.rhunk.snapenhance.util.ReflectionHelper
import java.lang.reflect.Field
import java.lang.reflect.Method
import java.lang.reflect.Modifier
import java.util.Arrays


class OperaPageViewControllerMapper : Mapper() {
    override fun useClasses(
        classLoader: ClassLoader,
        classes: List<Class<*>>,
        mappings: MutableMap<String, Any>
    ) {
        var operaPageViewControllerClass: Class<*>? = null
        for (aClass in classes) {
            if (!Modifier.isAbstract(aClass.modifiers)) continue
            if (aClass.interfaces.isEmpty()) continue
            val foundFields = Arrays.stream(aClass.declaredFields).filter { field: Field ->
                val modifiers = field.modifiers
                Modifier.isStatic(modifiers) && Modifier.isFinal(
                    modifiers
                )
            }.filter { field: Field ->
                try {
                    return@filter "ad_product_type" == String.format("%s", field[null])
                } catch (e: IllegalAccessException) {
                    e.printStackTrace()
                }
                false
            }.count()
            if (foundFields == 0L) continue
            operaPageViewControllerClass = aClass
            break
        }
        if (operaPageViewControllerClass == null) throw RuntimeException("OperaPageViewController not found")

        val members = HashMap<String, String>()
        members["Class"] = operaPageViewControllerClass.name

        operaPageViewControllerClass.fields.forEach { field ->
            val fieldType = field.type
            if (fieldType.isEnum) {
                fieldType.enumConstants.firstOrNull { enumConstant: Any -> enumConstant.toString() == "FULLY_DISPLAYED" }
                    .let { members["viewStateField"] = field.name }
            }
            if (fieldType == ArrayList::class.java) {
                members["layerListField"] = field.name
            }
        }
        val enumViewStateClass = operaPageViewControllerClass.fields.first { field: Field ->
            field.name == members["viewStateField"]
        }.type

        //find the method that call the onDisplayStateChange method
        members["onDisplayStateChange"] =
            operaPageViewControllerClass.methods.first { method: Method ->
                if (method.returnType != Void.TYPE || method.parameterTypes.size != 1) return@first false
                val firstParameterClass = method.parameterTypes[0]
                //check if the class contains a field with the enumViewStateClass type
                ReflectionHelper.searchFieldByType(firstParameterClass, enumViewStateClass) != null
            }.name

        //find the method that call the onDisplayStateChange method from gestures
        members["onDisplayStateChange2"] =
            operaPageViewControllerClass.methods.first { method: Method ->
                if (method.returnType != Void.TYPE || method.parameterTypes.size != 2) return@first false
                val firstParameterClass = method.parameterTypes[0]
                val secondParameterClass = method.parameterTypes[1]
                firstParameterClass.isEnum && secondParameterClass.isEnum
            }.name

        mappings["OperaPageViewController"] = members
    }
}

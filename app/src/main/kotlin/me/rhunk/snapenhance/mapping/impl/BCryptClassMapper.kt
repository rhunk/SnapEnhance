package me.rhunk.snapenhance.mapping.impl

import me.rhunk.snapenhance.mapping.Mapper
import java.lang.reflect.Modifier

class BCryptClassMapper : Mapper() {
    override fun useClasses(
        classLoader: ClassLoader,
        classes: List<Class<*>>,
        mappings: MutableMap<String, Any>
    ) {
        for (clazz in classes) {
            if (!Modifier.isFinal(clazz.modifiers)) continue
            clazz.declaredFields.firstOrNull { it.type == IntArray::class.java && Modifier.isStatic(it.modifiers)}?.let { field ->
                val fieldData = field.get(null)
                if (fieldData !is IntArray) return@let
                if (fieldData.size != 18 || fieldData[0] != 608135816) return@let
                mappings["BCryptClass"] = clazz.name
                mappings["BCryptClassHashMethod"] = clazz.methods.first {
                    it.parameterTypes.size == 2 && it.returnType == String::class.java && it.parameterTypes[0] == String::class.java && it.parameterTypes[1] == String::class.java
                }.name
                return
            }
        }
    }
}
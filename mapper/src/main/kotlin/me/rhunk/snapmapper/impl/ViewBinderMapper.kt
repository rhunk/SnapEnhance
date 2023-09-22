package me.rhunk.snapmapper.impl

import me.rhunk.snapmapper.AbstractClassMapper
import me.rhunk.snapmapper.MapperContext
import me.rhunk.snapmapper.ext.getClassName
import me.rhunk.snapmapper.ext.isAbstract
import me.rhunk.snapmapper.ext.isInterface
import java.lang.reflect.Modifier

class ViewBinderMapper  : AbstractClassMapper() {
    override fun run(context: MapperContext) {
        for (clazz in context.classes) {
            if (!clazz.isAbstract() || clazz.isInterface()) continue

            val getViewMethod = clazz.methods.firstOrNull { it.returnType == "Landroid/view/View;" && it.parameterTypes.size == 0 } ?: continue

            // update view
            clazz.methods.filter {
                Modifier.isAbstract(it.accessFlags) && it.parameterTypes.size == 1 && it.parameterTypes[0] == "Landroid/view/View;" && it.returnType == "V"
            }.also {
                if (it.size != 1) return@also
            }.firstOrNull() ?: continue

            val bindMethod = clazz.methods.filter {
                Modifier.isAbstract(it.accessFlags) && it.parameterTypes.size == 2 && it.parameterTypes[0] == it.parameterTypes[1] && it.returnType == "V"
            }.also {
                if (it.size != 1) return@also
            }.firstOrNull() ?: continue

            context.addMapping("ViewBinder",
                "class" to clazz.getClassName(),
                "bindMethod" to bindMethod.name,
                "getViewMethod" to getViewMethod.name
            )
        }
    }
}
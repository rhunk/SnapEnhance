package me.rhunk.snapmapper.impl

import me.rhunk.snapmapper.AbstractClassMapper
import me.rhunk.snapmapper.MapperContext
import me.rhunk.snapmapper.ext.getClassName
import me.rhunk.snapmapper.ext.getSuperClassName
import me.rhunk.snapmapper.ext.isFinal

class CallbackMapper : AbstractClassMapper() {
    override fun run(context: MapperContext) {
        val callbackClasses = context.classes.filter { clazz ->
            if (clazz.superclass == null) return@filter false

            val superclassName = clazz.getSuperClassName()!!
            if (!superclassName.endsWith("Callback") || superclassName.endsWith("\$Callback")) return@filter false

            val superClass = context.getClass(clazz.superclass) ?: return@filter false
            if (superClass.isFinal()) return@filter false

            superClass.virtualMethods.any { it.name == "onError" }
        }.map {
             it.getSuperClassName()!!.substringAfterLast("/") to it.getClassName()
        }

        context.addMapping("callbacks", *callbackClasses.toTypedArray())
    }
}
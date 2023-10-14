package me.rhunk.snapenhance.mapper.impl

import me.rhunk.snapenhance.mapper.AbstractClassMapper
import me.rhunk.snapenhance.mapper.MapperContext
import me.rhunk.snapenhance.mapper.ext.getClassName
import me.rhunk.snapenhance.mapper.ext.getSuperClassName
import me.rhunk.snapenhance.mapper.ext.isFinal

class CallbackMapper : AbstractClassMapper() {
    override fun run(context: MapperContext) {
        val callbackClasses = context.classes.filter { clazz ->
            if (clazz.superclass == null) return@filter false

            val superclassName = clazz.getSuperClassName()!!
            if ((!superclassName.endsWith("Callback") && !superclassName.endsWith("Delegate"))
                || superclassName.endsWith("\$Callback")) return@filter false

            if (clazz.getClassName().endsWith("\$CppProxy")) return@filter false

            val superClass = context.getClass(clazz.superclass) ?: return@filter false
            !superClass.isFinal()
        }.map {
             it.getSuperClassName()!!.substringAfterLast("/") to it.getClassName()
        }

        context.addMapping("callbacks", *callbackClasses.toTypedArray())
    }
}

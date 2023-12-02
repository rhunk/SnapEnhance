package me.rhunk.snapenhance.mapper.impl

import me.rhunk.snapenhance.mapper.AbstractClassMapper
import me.rhunk.snapenhance.mapper.ext.getClassName
import me.rhunk.snapenhance.mapper.ext.getSuperClassName
import me.rhunk.snapenhance.mapper.ext.isFinal
import org.jf.dexlib2.Opcode
import org.jf.dexlib2.iface.instruction.formats.Instruction21t
import org.jf.dexlib2.iface.instruction.formats.Instruction22t

class CallbackMapper : AbstractClassMapper() {
    init {
        mapper {
            val callbackClasses = classes.filter { clazz ->
                if (clazz.superclass == null) return@filter false

                val superclassName = clazz.getSuperClassName()!!
                if ((!superclassName.endsWith("Callback") && !superclassName.endsWith("Delegate"))
                    || superclassName.endsWith("\$Callback")) return@filter false

                if (clazz.getClassName().endsWith("\$CppProxy")) return@filter false

                // ignore dummy ContentCallback class
                if (superclassName.endsWith("ContentCallback") && clazz.methods.none { it.name == "handleContentResult" && it.implementation?.instructions?.firstOrNull { instruction ->
                    instruction is Instruction22t || instruction is Instruction21t
                } != null})
                    return@filter false

                val superClass = getClass(clazz.superclass) ?: return@filter false
                !superClass.isFinal()
            }.map {
                it.getSuperClassName()!!.substringAfterLast("/") to it.getClassName()
            }

            addMapping("callbacks", *callbackClasses.toTypedArray())
        }
    }
}
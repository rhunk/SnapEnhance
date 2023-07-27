package me.rhunk.snapmapper.impl

import me.rhunk.snapmapper.AbstractClassMapper
import me.rhunk.snapmapper.MapperContext
import me.rhunk.snapmapper.ext.getStaticConstructor
import me.rhunk.snapmapper.ext.isFinal
import org.jf.dexlib2.iface.instruction.formats.ArrayPayload

class BCryptClassMapper : AbstractClassMapper() {
    override fun run(context: MapperContext) {
        for (clazz in context.classes) {
            if (!clazz.isFinal()) continue

            val isBcryptClass = clazz.getStaticConstructor()?.let { constructor ->
                constructor.implementation?.instructions?.filterIsInstance<ArrayPayload>()?.any { it.arrayElements.size == 18 && it.arrayElements[0] == 608135816 }
            }

            if (isBcryptClass == true) {
                val hashMethod = clazz.methods.first {
                    it.parameterTypes.size == 2 &&
                    it.parameterTypes[0] == "Ljava/lang/String;" &&
                    it.parameterTypes[1] == "Ljava/lang/String;" &&
                    it.returnType == "Ljava/lang/String;"
                }

                context.addMapping("BCrypt",
                    "class" to clazz.type.replace("L", "").replace(";", ""),
                    "hashMethod" to hashMethod.name
                )
                return
            }
        }
    }
}
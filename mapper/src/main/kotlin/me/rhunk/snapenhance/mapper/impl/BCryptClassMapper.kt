package me.rhunk.snapenhance.mapper.impl

import me.rhunk.snapenhance.mapper.AbstractClassMapper
import me.rhunk.snapenhance.mapper.ext.getClassName
import me.rhunk.snapenhance.mapper.ext.getStaticConstructor
import me.rhunk.snapenhance.mapper.ext.isFinal
import org.jf.dexlib2.iface.instruction.formats.ArrayPayload

class BCryptClassMapper : AbstractClassMapper() {
    init {
        mapper {
            for (clazz in classes) {
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

                    addMapping("BCrypt",
                        "class" to clazz.getClassName(),
                        "hashMethod" to hashMethod.name
                    )
                    return@mapper
                }
            }
        }
    }
}
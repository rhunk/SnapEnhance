package me.rhunk.snapenhance.mapper.impl

import me.rhunk.snapenhance.mapper.AbstractClassMapper
import me.rhunk.snapenhance.mapper.ext.getClassName
import me.rhunk.snapenhance.mapper.ext.isAbstract
import me.rhunk.snapenhance.mapper.ext.isInterface
import java.lang.reflect.Modifier

class ViewBinderMapper  : AbstractClassMapper("ViewBinder") {
    val classReference = classReference("class")
    val bindMethod = string("bindMethod")
    val getViewMethod = string("getViewMethod")

    init {
        mapper {
            for (clazz in classes) {
                if (!clazz.isAbstract() || clazz.isInterface()) continue

                val getViewDexMethod = clazz.methods.firstOrNull { it.returnType == "Landroid/view/View;" && it.parameterTypes.size == 0 } ?: continue

                // update view
                clazz.methods.filter {
                    Modifier.isAbstract(it.accessFlags) && it.parameterTypes.size == 1 && it.parameterTypes[0] == "Landroid/view/View;" && it.returnType == "V"
                }.also {
                    if (it.size != 1) return@also
                }.firstOrNull() ?: continue

                val bindDexMethod = clazz.methods.filter {
                    Modifier.isAbstract(it.accessFlags) && it.parameterTypes.size == 2 && it.parameterTypes[0] == it.parameterTypes[1] && it.returnType == "V"
                }.also {
                    if (it.size != 1) return@also
                }.firstOrNull() ?: continue

                classReference.set(clazz.getClassName())
                bindMethod.set(bindDexMethod.name)
                getViewMethod.set(getViewDexMethod.name)
                return@mapper
            }
        }
    }
}
package me.rhunk.snapenhance.mapper.impl

import me.rhunk.snapenhance.mapper.AbstractClassMapper
import me.rhunk.snapenhance.mapper.ext.getClassName
import me.rhunk.snapenhance.mapper.ext.isAbstract

class DefaultMediaItemMapper : AbstractClassMapper() {
    init {
        mapper {
            for (clazz in classes) {
                val superClass = getClass(clazz.superclass) ?: continue

                if (!superClass.isAbstract() || superClass.interfaces.isEmpty() || superClass.interfaces[0] != "Ljava/lang/Comparable;") continue
                if (clazz.methods.none { it.returnType == "Landroid/net/Uri;" }) continue

                val constructorParameters = clazz.directMethods.firstOrNull { it.name == "<init>" }?.parameterTypes ?: continue
                if (constructorParameters.size < 6 || constructorParameters[5] != "J") continue

                addMapping("DefaultMediaItem", clazz.getClassName())
                return@mapper
            }
        }
    }
}
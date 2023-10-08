package me.rhunk.snapenhance.mapper.impl

import me.rhunk.snapenhance.mapper.AbstractClassMapper
import me.rhunk.snapenhance.mapper.ext.findConstString
import me.rhunk.snapenhance.mapper.ext.getClassName
import me.rhunk.snapenhance.mapper.ext.getStaticConstructor
import me.rhunk.snapenhance.mapper.ext.isEnum

class PlatformAnalyticsCreatorMapper : AbstractClassMapper() {
    init {
        mapper {
            for (clazz in classes) {
                val firstConstructor = clazz.directMethods.firstOrNull { it.name == "<init>" } ?: continue
                // 47 is the number of parameters of the constructor
                // it may change in future versions
                if (firstConstructor.parameters.size != 47) continue
                val firstParameterClass = getClass(firstConstructor.parameterTypes[0]) ?: continue
                if (!firstParameterClass.isEnum()) continue
                if (firstParameterClass.getStaticConstructor()?.implementation?.findConstString("IN_APP_NOTIFICATION") != true) continue

                addMapping("PlatformAnalyticsCreator", clazz.getClassName())
                return@mapper
            }
        }
    }
}
package me.rhunk.snapenhance.mapper.impl

import me.rhunk.snapenhance.mapper.AbstractClassMapper
import me.rhunk.snapenhance.mapper.ext.findConstString
import me.rhunk.snapenhance.mapper.ext.getClassName
import me.rhunk.snapenhance.mapper.ext.getStaticConstructor
import me.rhunk.snapenhance.mapper.ext.isEnum

class ScCameraSettingsMapper : AbstractClassMapper() {
    init {
        mapper {
            for (clazz in classes) {
                val firstConstructor = clazz.directMethods.firstOrNull { it.name == "<init>" } ?: continue
                if (firstConstructor.parameterTypes.size < 27) continue
                val firstParameter = getClass(firstConstructor.parameterTypes[0]) ?: continue
                if (!firstParameter.isEnum() || firstParameter.getStaticConstructor()?.implementation?.findConstString("CONTINUOUS_PICTURE") != true) continue

                addMapping("ScCameraSettings", clazz.getClassName())
                return@mapper
            }
        }
    }
}
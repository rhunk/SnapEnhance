package me.rhunk.snapenhance.mapper.impl

import me.rhunk.snapenhance.mapper.AbstractClassMapper
import me.rhunk.snapenhance.mapper.MapperContext
import me.rhunk.snapenhance.mapper.ext.findConstString
import me.rhunk.snapenhance.mapper.ext.getStaticConstructor
import me.rhunk.snapenhance.mapper.ext.isEnum

class ScCameraSettingsMapper : AbstractClassMapper() {
    override fun run(context: MapperContext) {
        for (clazz in context.classes) {
            val firstConstructor = clazz.directMethods.firstOrNull { it.name == "<init>" } ?: continue
            if (firstConstructor.parameterTypes.size < 27) continue
            val firstParameter = context.getClass(firstConstructor.parameterTypes[0]) ?: continue
            if (!firstParameter.isEnum() || firstParameter.getStaticConstructor()?.implementation?.findConstString("CONTINUOUS_PICTURE") != true) continue

            context.addMapping("ScCameraSettings", clazz.type.replace("L", "").replace(";", ""))
            return
        }
    }
}
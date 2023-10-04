package me.rhunk.snapenhance.mapper.impl

import me.rhunk.snapenhance.mapper.AbstractClassMapper
import me.rhunk.snapenhance.mapper.MapperContext
import me.rhunk.snapenhance.mapper.ext.getClassName
import me.rhunk.snapenhance.mapper.ext.hasStaticConstructorString
import me.rhunk.snapenhance.mapper.ext.isEnum

class EnumMapper : AbstractClassMapper() {
    override fun run(context: MapperContext) {
        lateinit var enumQualityLevel : String

        for (enumClass in context.classes) {
            if (!enumClass.isEnum()) continue

            if (enumClass.hasStaticConstructorString("LEVEL_MAX")) {
                enumQualityLevel = enumClass.getClassName()
                break;
            }
        }

        context.addMapping("EnumQualityLevel", enumQualityLevel)
    }
}
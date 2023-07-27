package me.rhunk.snapmapper.impl

import me.rhunk.snapmapper.AbstractClassMapper
import me.rhunk.snapmapper.MapperContext
import me.rhunk.snapmapper.ext.findConstString
import me.rhunk.snapmapper.ext.getStaticConstructor
import me.rhunk.snapmapper.ext.isEnum

class PlatformAnalyticsCreatorMapper : AbstractClassMapper() {
    override fun run(context: MapperContext) {
        for (clazz in context.classes) {
            val firstConstructor = clazz.directMethods.firstOrNull { it.name == "<init>" } ?: continue
            // 47 is the number of parameters of the constructor
            // it may change in future versions
            if (firstConstructor.parameters.size != 47) continue
            val firstParameterClass = context.getClass(firstConstructor.parameterTypes[0]) ?: continue
            if (!firstParameterClass.isEnum()) continue
            if (firstParameterClass.getStaticConstructor()?.implementation?.findConstString("IN_APP_NOTIFICATION") != true) continue

            context.addMapping("PlatformAnalyticsCreator", clazz.type.replace("L", "").replace(";", ""))
        }
    }
}
package me.rhunk.snapmapper.impl

import me.rhunk.snapmapper.AbstractClassMapper
import me.rhunk.snapmapper.MapperContext
import me.rhunk.snapmapper.ext.findConstString

class StoryBoostStateMapper : AbstractClassMapper() {
    override fun run(context: MapperContext) {
        for (clazz in context.classes) {
            val firstConstructor = clazz.directMethods.firstOrNull { it.name == "<init>" } ?: continue
            if (firstConstructor.parameters.size != 3) continue
            if (firstConstructor.parameterTypes[1] != "J" || firstConstructor.parameterTypes[2] != "J") continue

            if (clazz.methods.firstOrNull { it.name == "toString" }?.implementation?.findConstString("StoryBoostState", contains = true) != true) continue

            context.addMapping("StoryBoostStateClass", clazz.type.replace("L", "").replace(";", ""))
            return
        }
    }
}
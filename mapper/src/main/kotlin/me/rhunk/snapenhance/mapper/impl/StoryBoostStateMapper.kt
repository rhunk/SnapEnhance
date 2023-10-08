package me.rhunk.snapenhance.mapper.impl

import me.rhunk.snapenhance.mapper.AbstractClassMapper
import me.rhunk.snapenhance.mapper.ext.findConstString
import me.rhunk.snapenhance.mapper.ext.getClassName

class StoryBoostStateMapper : AbstractClassMapper() {
    init {
        mapper {
            for (clazz in classes) {
                val firstConstructor = clazz.directMethods.firstOrNull { it.name == "<init>" } ?: continue
                if (firstConstructor.parameters.size != 3) continue
                if (firstConstructor.parameterTypes[1] != "J" || firstConstructor.parameterTypes[2] != "J") continue

                if (clazz.methods.firstOrNull { it.name == "toString" }?.implementation?.findConstString("StoryBoostState", contains = true) != true) continue

                addMapping("StoryBoostStateClass", clazz.getClassName())
                return@mapper
            }
        }
    }
}
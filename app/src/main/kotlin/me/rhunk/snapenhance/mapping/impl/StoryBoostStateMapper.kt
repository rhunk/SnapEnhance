package me.rhunk.snapenhance.mapping.impl

import me.rhunk.snapenhance.mapping.Mapper

class StoryBoostStateMapper : Mapper(){
    override fun useClasses(
        classLoader: ClassLoader,
        classes: List<Class<*>>,
        mappings: MutableMap<String, Any>
    ) {
        for (clazz in classes) {
            val firstConstructor = clazz.constructors.firstOrNull() ?: continue
            if (firstConstructor.parameterCount != 3) continue
            if (firstConstructor.parameterTypes[1] != Long::class.javaPrimitiveType || firstConstructor.parameterTypes[2] != Long::class.javaPrimitiveType) continue
            val storyBoostEnumClass = firstConstructor.parameterTypes[0]
            if (!storyBoostEnumClass.isEnum || storyBoostEnumClass.enumConstants.none { it.toString() == "NeedSubscriptionCannotSubscribe" }) continue
            mappings["StoryBoostStateClass"] = clazz.name
            return
        }
    }
}
package me.rhunk.snapenhance.mapping.impl

import me.rhunk.snapenhance.mapping.Mapper

class PlatformAnalyticsCreatorMapper : Mapper() {
    override fun useClasses(
        classLoader: ClassLoader,
        classes: List<Class<*>>,
        mappings: MutableMap<String, Any>
    ) {
        for (clazz in classes) {
            if (clazz.isEnum || clazz.isInterface) continue
            val constructors = clazz.constructors
            if (constructors.isEmpty()) continue
            val firstConstructor = constructors[0]
            // 47 is the number of parameters of the constructor
            // can change in future versions
            if (firstConstructor.parameterCount != 47) continue
            if (!firstConstructor.parameterTypes[0].isEnum) continue
            if (firstConstructor.parameterTypes[0].enumConstants.none { it.toString() == "IN_APP_NOTIFICATION" }) continue

            mappings["PlatformAnalyticsCreator"] = clazz.name
            return
        }
    }
}
package me.rhunk.snapenhance.mapping.impl

import me.rhunk.snapenhance.mapping.Mapper

class ScCameraSettingsMapper : Mapper() {
    override fun useClasses(
        classLoader: ClassLoader,
        classes: List<Class<*>>,
        mappings: MutableMap<String, Any>
    ) {
        for (clazz in classes) {
            if (clazz.constructors.isEmpty()) continue
            val parameters =  clazz.constructors.first().parameterTypes
            if (parameters.size < 27) continue
            val firstParameter = parameters[0]
            if (!firstParameter.isEnum || firstParameter.enumConstants.find { it.toString() == "CONTINUOUS_PICTURE" } == null) continue
            mappings["ScCameraSettings"] = clazz.name
            return
        }
    }
}
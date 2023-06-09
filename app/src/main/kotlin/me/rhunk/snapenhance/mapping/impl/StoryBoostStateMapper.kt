package me.rhunk.snapenhance.mapping.impl

import me.rhunk.snapenhance.mapping.Mapper

class StoryBoostStateMapper : Mapper(){
    override fun useClasses(
        classLoader: ClassLoader,
        classes: List<Class<*>>,
        mappings: MutableMap<String, Any>
    ) {
        for (clazz in classes) {
            val firstField = clazz.fields.firstOrNull() ?: continue
            if (!firstField.type.isEnum || firstField.type.enumConstants.none { it.toString() == "NeedSubscriptionCannotSubscribe" }) continue
            mappings["StoryBoostStateClass"] = clazz.name
            return
        }

    }
}
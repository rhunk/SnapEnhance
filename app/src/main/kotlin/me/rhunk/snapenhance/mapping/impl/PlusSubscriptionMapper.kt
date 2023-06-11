package me.rhunk.snapenhance.mapping.impl

import me.rhunk.snapenhance.mapping.Mapper
import java.lang.reflect.Modifier


class PlusSubscriptionMapper : Mapper() {
    override fun useClasses(
        classLoader: ClassLoader,
        classes: List<Class<*>>,
        mappings: MutableMap<String, Any>
    ) {
        for (clazz in classes) {
            clazz.declaredFields.firstOrNull {
                it.type == clazz &&
                Modifier.isFinal(it.modifiers) &&
                Modifier.isStatic(it.modifiers) &&
                runCatching {
                    it?.get(null).toString().startsWith("PlusSubscriptionState")
                }.getOrDefault(false)
            } ?: continue

            mappings["SubscriptionInfoClass"] = clazz.constructors[0]!!.parameterTypes[0]!!.name
            return
        }
    }
}
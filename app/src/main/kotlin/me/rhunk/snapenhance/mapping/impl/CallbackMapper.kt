package me.rhunk.snapenhance.mapping.impl

import me.rhunk.snapenhance.Logger.debug
import me.rhunk.snapenhance.mapping.Mapper
import java.lang.reflect.Method
import java.lang.reflect.Modifier

class CallbackMapper : Mapper() {
    override fun useClasses(
        classLoader: ClassLoader,
        classes: List<Class<*>>,
        mappings: MutableMap<String, Any>
    ) {
        val callbackMappings = HashMap<String, String>()
        classes.forEach { clazz ->
            val superClass = clazz.superclass ?: return@forEach
            if (!superClass.name.endsWith("Callback") || superClass.name.endsWith("\$Callback")) return@forEach
            if (!Modifier.isAbstract(superClass.modifiers)) return@forEach

            if (superClass.declaredMethods.any { method: Method ->
                    method.name == "onError"
                }) {
                callbackMappings[superClass.simpleName] = clazz.name
            }
        }
        debug("found " + callbackMappings.size + " callbacks")
        mappings["callbacks"] = callbackMappings
    }
}

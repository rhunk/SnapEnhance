package me.rhunk.snapenhance.mapping.impl

import android.net.Uri
import me.rhunk.snapenhance.mapping.Mapper
import java.lang.reflect.Modifier

class DefaultMediaItemMapper : Mapper() {
    override fun useClasses(
        classLoader: ClassLoader,
        classes: List<Class<*>>,
        mappings: MutableMap<String, Any>
    ) {
        for (clazz in classes) {
            if (clazz.superclass == null || !Modifier.isAbstract(clazz.superclass.modifiers)) continue
            if (clazz.superclass.interfaces.isEmpty() || clazz.superclass.interfaces[0] != Comparable::class.java) continue
            if (clazz.methods.none { it.returnType == Uri::class.java }) continue

            val constructorParameters = clazz.constructors[0]?.parameterTypes ?: continue
            if (constructorParameters.size < 6 || constructorParameters[5] != Long::class.javaPrimitiveType) continue

            mappings["DefaultMediaItem"] = clazz.name
        }
    }
}
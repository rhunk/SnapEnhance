package me.rhunk.snapenhance.mapping.impl

import me.rhunk.snapenhance.Logger
import me.rhunk.snapenhance.mapping.Mapper

class GridMediaItemMapper : Mapper() {
    override fun useClasses(
        classLoader: ClassLoader,
        classes: List<Class<*>>,
        mappings: MutableMap<String, Any>
    ) {
        for (clazz in classes) {
            if (clazz.isEnum || clazz.isInterface) continue
            if (clazz.annotations.isEmpty()) continue
            if (!clazz.annotations[0].toString().contains("typeReferences")) continue
            clazz.declaredFields.firstOrNull {
                it.annotations.isNotEmpty() && it.annotations[0].toString().contains("cameraRollSource")
            }?.let {
                mappings["GridMediaItem"] = clazz.name
                mappings["GridMediaItemDurationField"] = clazz.declaredFields.first {
                    it.annotations.isNotEmpty() && it.annotations[0].toString().contains("durationMs")
                }.name

                Logger.debug("Found GridMediaItem: ${clazz.name}, durationMs: ${mappings["GridMediaItemDurationField"]}")
            }
        }
    }
}
package me.rhunk.snapenhance.mapping

import me.rhunk.snapenhance.ModContext

abstract class Mapper {
    lateinit var context: ModContext

    abstract fun useClasses(
        classLoader: ClassLoader,
        classes: List<Class<*>>,
        mappings: MutableMap<String, Any>
    )
}

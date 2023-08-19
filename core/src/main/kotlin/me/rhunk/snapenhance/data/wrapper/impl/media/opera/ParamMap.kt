package me.rhunk.snapenhance.data.wrapper.impl.media.opera

import me.rhunk.snapenhance.data.wrapper.AbstractWrapper
import me.rhunk.snapenhance.util.ReflectionHelper
import me.rhunk.snapenhance.util.ktx.getObjectField
import java.lang.reflect.Field
import java.util.concurrent.ConcurrentHashMap

@Suppress("UNCHECKED_CAST")
class ParamMap(obj: Any?) : AbstractWrapper(obj) {
    private val paramMapField: Field by lazy {
        ReflectionHelper.searchFieldTypeInSuperClasses(
            instanceNonNull().javaClass,
            ConcurrentHashMap::class.java
        )!!
    }

    val concurrentHashMap: ConcurrentHashMap<Any, Any>
        get() = instanceNonNull().getObjectField(paramMapField.name) as ConcurrentHashMap<Any, Any>

    operator fun get(key: String): Any? {
        return concurrentHashMap.keys.firstOrNull{ k: Any -> k.toString() == key }?.let { concurrentHashMap[it] }
    }

    fun put(key: String, value: Any) {
        val keyObject = concurrentHashMap.keys.firstOrNull { k: Any -> k.toString() == key } ?: key
        concurrentHashMap[keyObject] = value
    }

    fun containsKey(key: String): Boolean {
        return concurrentHashMap.keys.any { k: Any -> k.toString() == key }
    }

    override fun toString(): String {
        return concurrentHashMap.toString()
    }
}

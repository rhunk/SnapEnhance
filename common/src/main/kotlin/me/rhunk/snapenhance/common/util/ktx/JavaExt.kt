package me.rhunk.snapenhance.common.util.ktx

import java.lang.reflect.Field

fun String.longHashCode(): Long {
    var h = 1125899906842597L
    for (element in this) h = 31 * h + element.code.toLong()
    return h
}

inline fun Class<*>.findFields(once: Boolean, crossinline predicate: (field: Field) -> Boolean): List<Field>{
    var clazz: Class<*>? = this
    val fields = mutableListOf<Field>()

    while (clazz != null) {
        if (once) {
            clazz.declaredFields.firstOrNull(predicate)?.let { return listOf(it) }
        } else {
            fields.addAll(clazz.declaredFields.filter(predicate))
        }
        clazz = clazz.superclass ?: break
    }

    return fields
}

inline fun Class<*>.findFieldsToString(instance: Any? = null, once: Boolean = false, crossinline predicate: (field: Field, value: String) -> Boolean): List<Field> {
    return this.findFields(once = once) {
        try {
            it.isAccessible = true
            return@findFields it.get(instance)?.let { it1 -> predicate(it, it1.toString()) } == true
        } catch (e: Throwable) {
            return@findFields false
        }
    }
}

package me.rhunk.snapenhance.core.util.ktx

import de.robv.android.xposed.XposedHelpers

fun Any.getObjectField(fieldName: String): Any? {
    return XposedHelpers.getObjectField(this, fieldName)
}

fun Any.setEnumField(fieldName: String, value: String) {
    this::class.java.getDeclaredField(fieldName)
        .type.enumConstants?.firstOrNull { it.toString() == value }?.let { enum ->
        setObjectField(fieldName, enum)
    }
}

fun Any.setObjectField(fieldName: String, value: Any?) {
    XposedHelpers.setObjectField(this, fieldName, value)
}

fun Any.getObjectFieldOrNull(fieldName: String): Any? {
    return try {
        getObjectField(fieldName)
    } catch (t: Throwable) {
        null
    }
}


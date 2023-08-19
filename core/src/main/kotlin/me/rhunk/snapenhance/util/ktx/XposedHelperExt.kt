package me.rhunk.snapenhance.util.ktx

import de.robv.android.xposed.XposedHelpers

fun Any.getObjectField(fieldName: String): Any? {
    return XposedHelpers.getObjectField(this, fieldName)
}

fun Any.setObjectField(fieldName: String, value: Any?) {
    XposedHelpers.setObjectField(this, fieldName, value)
}

fun Any.getObjectFieldOrNull(fieldName: String): Any? {
    return try {
        getObjectField(fieldName)
    } catch (e: Exception) {
        null
    }
}


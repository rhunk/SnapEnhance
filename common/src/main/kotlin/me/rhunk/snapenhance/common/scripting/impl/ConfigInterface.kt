package me.rhunk.snapenhance.common.scripting.impl

import org.mozilla.javascript.annotations.JSFunction


enum class ConfigTransactionType(
    val key: String
) {
    GET("get"),
    SET("set"),
    SAVE("save"),
    LOAD("load"),
    DELETE("delete");

    companion object {
        fun fromKey(key: String) = entries.find { it.key == key }
    }
}


abstract class ConfigInterface {
    @JSFunction fun get(key: String): String? = get(key, null)
    @JSFunction abstract fun get(key: String, defaultValue: Any?): String?

    @JSFunction fun getInteger(key: String): Int? = getInteger(key, null)
    @JSFunction fun getInteger(key: String, defaultValue: Int?): Int? = get(key, defaultValue.toString())?.toIntOrNull() ?: defaultValue

    @JSFunction fun getDouble(key: String): Double? = getDouble(key, null)
    @JSFunction fun getDouble(key: String, defaultValue: Double?): Double? = get(key, defaultValue.toString())?.toDoubleOrNull() ?: defaultValue

    @JSFunction fun getBoolean(key: String): Boolean? = getBoolean(key, null)
    @JSFunction fun getBoolean(key: String, defaultValue: Boolean?): Boolean? = get(key, defaultValue.toString())?.toBoolean() ?: defaultValue

    @JSFunction fun getLong(key: String): Long? = getLong(key, null)
    @JSFunction fun getLong(key: String, defaultValue: Long?): Long? = get(key, defaultValue.toString())?.toLongOrNull() ?: defaultValue

    @JSFunction fun getFloat(key: String): Float? = getFloat(key, null)
    @JSFunction fun getFloat(key: String, defaultValue: Float?): Float? = get(key, defaultValue.toString())?.toFloatOrNull() ?: defaultValue

    @JSFunction fun getByte(key: String): Byte? = getByte(key, null)
    @JSFunction fun getByte(key: String, defaultValue: Byte?): Byte? = get(key, defaultValue.toString())?.toByteOrNull() ?: defaultValue

    @JSFunction fun getShort(key: String): Short? = getShort(key, null)
    @JSFunction fun getShort(key: String, defaultValue: Short?): Short? = get(key, defaultValue.toString())?.toShortOrNull() ?: defaultValue


    @JSFunction fun set(key: String, value: Any?) = set(key, value, false)
    @JSFunction abstract fun set(key: String, value: Any?, save: Boolean)

    @JSFunction fun setInteger(key: String, value: Int?) = setInteger(key, value, false)
    @JSFunction fun setInteger(key: String, value: Int?, save: Boolean) = set(key, value, save)

    @JSFunction fun setDouble(key: String, value: Double?) = setDouble(key, value, false)
    @JSFunction fun setDouble(key: String, value: Double?, save: Boolean) = set(key, value, save)

    @JSFunction fun setBoolean(key: String, value: Boolean?) = setBoolean(key, value, false)
    @JSFunction fun setBoolean(key: String, value: Boolean?, save: Boolean) = set(key, value, save)

    @JSFunction fun setLong(key: String, value: Long?) = setLong(key, value, false)
    @JSFunction fun setLong(key: String, value: Long?, save: Boolean) = set(key, value, save)

    @JSFunction fun setFloat(key: String, value: Float?) = setFloat(key, value, false)
    @JSFunction fun setFloat(key: String, value: Float?, save: Boolean) = set(key, value, save)

    @JSFunction fun setByte(key: String, value: Byte?) = setByte(key, value, false)
    @JSFunction fun setByte(key: String, value: Byte?, save: Boolean) = set(key, value, save)

    @JSFunction fun setShort(key: String, value: Short?) = setShort(key, value, false)
    @JSFunction fun setShort(key: String, value: Short?, save: Boolean) = set(key, value, save)

    @JSFunction abstract fun save()
    @JSFunction abstract fun load()
    @JSFunction abstract fun delete()
}
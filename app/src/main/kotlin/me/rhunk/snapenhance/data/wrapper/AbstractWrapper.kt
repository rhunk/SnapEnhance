package me.rhunk.snapenhance.data.wrapper

import de.robv.android.xposed.XposedHelpers

abstract class AbstractWrapper(
    protected var instance: Any
) {
    fun instance() = instance

    override fun hashCode(): Int {
        return instance.hashCode()
    }

    override fun toString(): String {
        return instance.toString()
    }


    fun <T : Enum<*>> getEnumValue(fieldName: String, defaultValue: T): T {
        val mContentType = XposedHelpers.getObjectField(instance, fieldName) as Enum<*>
        return java.lang.Enum.valueOf(defaultValue::class.java, mContentType.name) as T
    }

    @Suppress("UNCHECKED_CAST")
    fun setEnumValue(fieldName: String, value: Enum<*>) {
        val type = instance.javaClass.fields.find { it.name == fieldName }?.type as Class<out Enum<*>>
        XposedHelpers.setObjectField(instance, fieldName, java.lang.Enum.valueOf(type, value.name))
    }
}
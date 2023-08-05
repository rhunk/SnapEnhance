package me.rhunk.snapenhance.data.wrapper

import de.robv.android.xposed.XposedHelpers
import me.rhunk.snapenhance.util.CallbackBuilder
import kotlin.reflect.KProperty

abstract class AbstractWrapper(
    protected var instance: Any?
) {
    @Suppress("UNCHECKED_CAST")
    inner class EnumAccessor<T>(private val fieldName: String, private val defaultValue: T) {
        operator fun getValue(obj: Any, property: KProperty<*>): T = getEnumValue(fieldName, defaultValue as Enum<*>) as T
        operator fun setValue(obj: Any, property: KProperty<*>, value: Any) = setEnumValue(fieldName, value as Enum<*>)
    }

    companion object {
        fun newEmptyInstance(clazz: Class<*>): Any {
            return CallbackBuilder.createEmptyObject(clazz.constructors[0]) ?: throw NullPointerException()
        }
    }

    fun instanceNonNull(): Any = instance!!
    fun isPresent(): Boolean = instance != null

    override fun hashCode(): Int {
        return instance.hashCode()
    }

    override fun toString(): String {
        return instance.toString()
    }

    protected fun <T> enum(fieldName: String, defaultValue: T) = EnumAccessor(fieldName, defaultValue)

    fun <T : Enum<*>> getEnumValue(fieldName: String, defaultValue: T): T {
        val mContentType = XposedHelpers.getObjectField(instance, fieldName) as Enum<*>
        return java.lang.Enum.valueOf(defaultValue::class.java, mContentType.name) as T
    }

    @Suppress("UNCHECKED_CAST")
    fun setEnumValue(fieldName: String, value: Enum<*>) {
        val type = instance!!.javaClass.declaredFields.find { it.name == fieldName }?.type as Class<out Enum<*>>
        XposedHelpers.setObjectField(instance, fieldName, java.lang.Enum.valueOf(type, value.name))
    }
}
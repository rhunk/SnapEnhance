package me.rhunk.snapenhance.core.wrapper

import de.robv.android.xposed.XposedHelpers
import me.rhunk.snapenhance.core.util.CallbackBuilder
import me.rhunk.snapenhance.core.wrapper.impl.SnapUUID
import kotlin.reflect.KProperty

abstract class AbstractWrapper(
    protected var instance: Any?
) {
    protected val uuidArrayListMapper: (Any?) -> ArrayList<SnapUUID> get() = { (it as ArrayList<*>).map { i -> SnapUUID(i) }.toCollection(ArrayList()) }

    @Suppress("UNCHECKED_CAST")
    inner class EnumAccessor<T>(private val fieldName: String, private val defaultValue: T) {
        operator fun getValue(obj: Any, property: KProperty<*>): T? = getEnumValue(fieldName, defaultValue as Enum<*>) as? T
        operator fun setValue(obj: Any, property: KProperty<*>, value: Any?) = setEnumValue(fieldName, value as Enum<*>)
    }

    inner class FieldAccessor<T>(private val fieldName: String, private val mapper: ((Any?) -> T?)? = null) {
        @Suppress("UNCHECKED_CAST")
        operator fun getValue(obj: Any, property: KProperty<*>): T? {
            val value = XposedHelpers.getObjectField(instance, fieldName)
            return if (mapper != null) {
                mapper.invoke(value)
            } else {
                value as? T
            }
        }

        operator fun setValue(obj: Any, property: KProperty<*>, value: Any?) {
            XposedHelpers.setObjectField(instance, fieldName, when (value) {
                is AbstractWrapper -> value.instance
                is ArrayList<*> -> value.map { if (it is AbstractWrapper) it.instance else it }.toMutableList()
                else -> value
            })
        }
    }

    companion object {
        fun newEmptyInstance(clazz: Class<*>): Any {
            return CallbackBuilder.createEmptyObject(clazz.constructors[0]) ?: throw NullPointerException()
        }
    }

    fun instanceNonNull(): Any = instance ?: throw NullPointerException("Instance of ${this::class.simpleName} is null")
    fun isPresent(): Boolean = instance != null

    override fun hashCode(): Int {
        return instance.hashCode()
    }

    override fun toString(): String {
        return instance.toString()
    }

    protected fun <T> enum(fieldName: String, defaultValue: T) = EnumAccessor(fieldName, defaultValue)
    protected fun <T> field(fieldName: String, mapper: ((Any?) -> T?)? = null) = FieldAccessor(fieldName, mapper)

    fun <T : Enum<*>> getEnumValue(fieldName: String, defaultValue: T?): T? {
        if (defaultValue == null || instance == null) return null
        val mContentType = XposedHelpers.getObjectField(instance, fieldName) as? Enum<*> ?: return null
        return java.lang.Enum.valueOf(defaultValue::class.java, mContentType.name)
    }

    @Suppress("UNCHECKED_CAST")
    fun setEnumValue(fieldName: String, value: Enum<*>) {
        val type = instance!!.javaClass.declaredFields.find { it.name == fieldName }?.type as Class<out Enum<*>>
        XposedHelpers.setObjectField(instance, fieldName, java.lang.Enum.valueOf(type, value.name))
    }
}
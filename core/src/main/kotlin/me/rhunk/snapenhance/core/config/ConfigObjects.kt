package me.rhunk.snapenhance.core.config

import me.rhunk.snapenhance.Logger
import kotlin.reflect.KProperty

class ConfigParams(
    var shouldTranslate: Boolean = false,
    var hidden: Boolean = false,
    var isFolder: Boolean = false
)

class PropertyValue<T>(
    private var value: T? = null,
    val defaultValues: List<*>? = null
) {
    inner class PropertyValueNullable {
        fun get() = value
        operator fun getValue(t: Any?, property: KProperty<*>): T? = getNullable()
        operator fun setValue(t: Any?, property: KProperty<*>, t1: T?) = set(t1)
    }

    fun nullable() = PropertyValueNullable()

    fun isSet() = value != null
    fun getNullable() = value?.takeIf { it != "null" }
    fun get() = getNullable() ?: throw IllegalStateException("Property is not set")
    fun set(value: T?) { this.value = value }
    @Suppress("UNCHECKED_CAST")
    fun setAny(value: Any?) { this.value = value as T? }

    operator fun getValue(t: Any?, property: KProperty<*>): T = get()
    operator fun setValue(t: Any?, property: KProperty<*>, t1: T?) = set(t1)
}

class PropertyKey<T>(
    val name: String,
    val dataProcessor: DataProcessors.PropertyDataProcessor<T>,
    val params: ConfigParams = ConfigParams(),
)


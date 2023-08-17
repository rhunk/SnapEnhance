package me.rhunk.snapenhance.core.config

import kotlin.reflect.KProperty


data class PropertyPair<T>(
    val key: PropertyKey<T>,
    val value: PropertyValue<*>
) {
    val name get() = key.name
}

class ConfigParams(
    private var _notices: Int? = null,
    var shouldTranslate: Boolean = true,
    var isHidden: Boolean = false,
    var isFolder: Boolean = false,
    var disabledKey: String? = null,
    var icon: String? = null
) {
    val notices get() = _notices?.let { FeatureNotice.values().filter { flag -> it and flag.id != 0 } } ?: emptyList()
    fun addNotices(vararg flags: FeatureNotice) {
        this._notices = (this._notices ?: 0) or flags.fold(0) { acc, featureNotice -> acc or featureNotice.id }
    }
}

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

data class PropertyKey<T>(
    private val _parent: () -> PropertyKey<*>?,
    val name: String,
    val dataType: DataProcessors.PropertyDataProcessor<T>,
    val params: ConfigParams = ConfigParams(),
) {
    val parentKey by lazy { _parent() }

    fun propertyTranslationPath(): String {
        return if (parentKey != null) {
            "${parentKey!!.propertyTranslationPath()}.properties.$name"
        } else {
            "features.properties.$name"
        }
    }
}


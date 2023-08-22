package me.rhunk.snapenhance.core.config

import me.rhunk.snapenhance.bridge.wrapper.LocaleWrapper
import kotlin.reflect.KProperty


data class PropertyPair<T>(
    val key: PropertyKey<T>,
    val value: PropertyValue<*>
) {
    val name get() = key.name
}

enum class FeatureNotice(
    val id: Int,
    val key: String
) {
    UNSTABLE(0b0001, "unstable"),
    MAY_BAN(0b0010, "may_ban"),
    MAY_BREAK_INTERNAL_BEHAVIOR(0b0100, "may_break_internal_behavior"),
    MAY_CAUSE_CRASHES(0b1000, "may_cause_crashes");
}

enum class ConfigFlag(
    val id: Int
) {
    NO_TRANSLATE(0b0001),
    HIDDEN(0b0010),
    FOLDER(0b0100),
    NO_DISABLE_KEY(0b1000)
}

class ConfigParams(
    private var _flags: Int? = null,
    private var _notices: Int? = null,

    var icon: String? = null,
    var disabledKey: String? = null,
    var customTranslationPath: String? = null,
    var customOptionTranslationPath: String? = null
) {
    val notices get() = _notices?.let { FeatureNotice.values().filter { flag -> it and flag.id != 0 } } ?: emptyList()
    val flags get() = _flags?.let { ConfigFlag.values().filter { flag -> it and flag.id != 0 } } ?: emptyList()

    fun addNotices(vararg values: FeatureNotice) {
        this._notices = (this._notices ?: 0) or values.fold(0) { acc, featureNotice -> acc or featureNotice.id }
    }

    fun addFlags(vararg values: ConfigFlag) {
        this._flags = (this._flags ?: 0) or values.fold(0) { acc, flag -> acc or flag.id }
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

    fun propertyOption(translation: LocaleWrapper, key: String): String {
        if (key == "null") {
            return translation[params.disabledKey ?: "manager.features.disabled"]
        }

        return if (!params.flags.contains(ConfigFlag.NO_TRANSLATE))
            translation[params.customOptionTranslationPath?.let {
                "$it.$key"
            } ?: "features.options.${name}.$key"]
        else key
    }

    fun propertyName() = propertyTranslationPath() + ".name"
    fun propertyDescription() = propertyTranslationPath() + ".description"

    fun propertyTranslationPath(): String {
        params.customTranslationPath?.let {
            return it
        }
        return parentKey?.let {
            "${it.propertyTranslationPath()}.properties.$name"
        } ?: "features.properties.$name"
    }
}


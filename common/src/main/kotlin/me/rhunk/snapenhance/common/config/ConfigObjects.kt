package me.rhunk.snapenhance.common.config

import me.rhunk.snapenhance.common.bridge.wrapper.LocaleWrapper
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
    BAN_RISK(0b0010, "ban_risk"),
    INTERNAL_BEHAVIOR(0b0100, "internal_behavior"),
    REQUIRE_NATIVE_HOOKS(0b1000, "require_native_hooks"),
}

enum class ConfigFlag(
    val id: Int
) {
    NO_TRANSLATE(0b000001),
    HIDDEN(0b000010),
    FOLDER(0b000100),
    NO_DISABLE_KEY(0b001000),
    REQUIRE_RESTART(0b010000),
    REQUIRE_CLEAN_CACHE(0b100000)
}

class ConfigParams(
    private var _flags: Int? = null,
    private var _notices: Int? = null,

    var icon: String? = null,
    var disabledKey: String? = null,
    var customTranslationPath: String? = null,
    var customOptionTranslationPath: String? = null,
    var inputCheck: ((String) -> Boolean)? = { true },
) {
    val notices get() = _notices?.let { FeatureNotice.entries.filter { flag -> it and flag.id != 0 } } ?: emptyList()
    val flags get() = _flags?.let { ConfigFlag.entries.filter { flag -> it and flag.id != 0 } } ?: emptyList()

    fun addNotices(vararg values: FeatureNotice) {
        this._notices = (this._notices ?: 0) or values.fold(0) { acc, featureNotice -> acc or featureNotice.id }
    }

    fun addFlags(vararg values: ConfigFlag) {
        this._flags = (this._flags ?: 0) or values.fold(0) { acc, flag -> acc or flag.id }
    }

    fun nativeHooks() {
        addNotices(FeatureNotice.REQUIRE_NATIVE_HOOKS)
    }

    fun requireRestart() {
        addFlags(ConfigFlag.REQUIRE_RESTART)
    }
    fun requireCleanCache() {
        addFlags(ConfigFlag.REQUIRE_CLEAN_CACHE)
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
    fun isEmpty() = value == null || value == "null" || value.toString().isEmpty()
    fun get() = getNullable() ?: throw IllegalStateException("Property is not set")
    fun set(value: T?) { setAny(value) }
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
    private val parentKey by lazy { _parent() }

    fun propertyOption(translation: LocaleWrapper, key: String): String {
        if (key == "null") {
            return translation[params.disabledKey ?: "manager.sections.features.disabled"]
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


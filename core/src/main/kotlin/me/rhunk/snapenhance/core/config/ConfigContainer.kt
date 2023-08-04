package me.rhunk.snapenhance.core.config

import com.google.gson.JsonObject
import kotlin.reflect.KProperty

typealias ConfigParamsBuilder = ConfigParams.() -> Unit

open class ConfigContainer(
    var globalState: Boolean? = null
) {
    var parentContainerKey: PropertyKey<*>? = null
    val properties = mutableMapOf<PropertyKey<*>, PropertyValue<*>>()

    private inline fun <T> registerProperty(
        key: String,
        type: DataProcessors.PropertyDataProcessor<*>,
        defaultValue: PropertyValue<T>,
        params: ConfigParams.() -> Unit = {},
        propertyKeyCallback: (PropertyKey<*>) -> Unit = {}
    ): PropertyValue<T> {
        val propertyKey = PropertyKey({ parentContainerKey }, key, type, ConfigParams().also { it.params() })
        properties[propertyKey] = defaultValue
        propertyKeyCallback(propertyKey)
        return defaultValue
    }

    protected fun boolean(key: String, defaultValue: Boolean = false, params: ConfigParamsBuilder = {}) =
        registerProperty(key, DataProcessors.BOOLEAN, PropertyValue(defaultValue), params)

    protected fun integer(key: String, defaultValue: Int = 0, params: ConfigParamsBuilder = {}) =
        registerProperty(key, DataProcessors.INTEGER, PropertyValue(defaultValue), params)

    protected fun float(key: String, defaultValue: Float = 0f, params: ConfigParamsBuilder = {}) =
        registerProperty(key, DataProcessors.FLOAT, PropertyValue(defaultValue), params)

    protected fun string(key: String, defaultValue: String = "", params: ConfigParamsBuilder = {}) =
        registerProperty(key, DataProcessors.STRING, PropertyValue(defaultValue), params)

    protected fun multiple(
        key: String,
        vararg values: String = emptyArray(),
        params: ConfigParamsBuilder = {}
    ) = registerProperty(key,
        DataProcessors.STRING_MULTIPLE_SELECTION, PropertyValue(mutableListOf<String>(), defaultValues = values.toList()), params)

    //null value is considered as Off/Disabled
    protected fun unique(
        key: String,
        vararg values: String = emptyArray(),
        params: ConfigParamsBuilder = {}
    ) = registerProperty(key,
        DataProcessors.STRING_UNIQUE_SELECTION, PropertyValue("null", defaultValues = values.toList()), params)

    protected fun <T : ConfigContainer> container(
        key: String,
        container: T
    ) = registerProperty(key, DataProcessors.container(container), PropertyValue(container)) {
        container.parentContainerKey = it
    }.get()

    fun toJson(): JsonObject {
        val json = JsonObject()
        properties.forEach { (propertyKey, propertyValue) ->
            val serializedValue = propertyValue.getNullable()?.let { propertyKey.dataType.serializeAny(it) }
            json.add(propertyKey.name, serializedValue)
        }
        return json
    }

    fun fromJson(json: JsonObject) {
        properties.forEach { (key, _) ->
            val jsonElement = json.get(key.name) ?: return@forEach
            key.dataType.deserializeAny(jsonElement)?.let {
                properties[key]?.setAny(it)
            }
        }
    }

    operator fun getValue(t: Any?, property: KProperty<*>) = this.globalState
    operator fun setValue(t: Any?, property: KProperty<*>, t1: Boolean?) { this.globalState = t1 }
}
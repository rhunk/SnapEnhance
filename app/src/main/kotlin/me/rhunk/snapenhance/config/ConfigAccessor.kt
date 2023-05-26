package me.rhunk.snapenhance.config

open class ConfigAccessor(
    private val configMap: MutableMap<ConfigProperty, ConfigValue<*>> = mutableMapOf()
) {
    fun bool(key: ConfigProperty): Boolean {
        return get(key).value() as Boolean
    }

    fun int(key: ConfigProperty): Int {
        return get(key).value() as Int
    }

    fun string(key: ConfigProperty): String {
        return get(key).value() as String
    }

    fun double(key: ConfigProperty): Double {
        return get(key).value() as Double
    }

    fun float(key: ConfigProperty): Float {
        return get(key).value() as Float
    }

    fun long(key: ConfigProperty): Long {
        return get(key).value() as Long
    }

    fun short(key: ConfigProperty): Short {
        return get(key).value() as Short
    }

    fun byte(key: ConfigProperty): Byte {
        return get(key).value() as Byte
    }

    fun char(key: ConfigProperty): Char {
        return get(key).value() as Char
    }

    @Suppress("UNCHECKED_CAST")
    fun options(key: ConfigProperty): Map<String, Boolean> {
        return get(key).value() as Map<String, Boolean>
    }

    fun get(key: ConfigProperty): ConfigValue<*> {
        return configMap[key]!!
    }

    fun set(key: ConfigProperty, value: ConfigValue<*>) {
        configMap[key] = value
    }

    fun entries(): Set<Map.Entry<ConfigProperty, ConfigValue<*>>> {
        return configMap.entries
    }
}
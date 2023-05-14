package me.rhunk.snapenhance.config

open class ConfigAccessor(
    private val configMap: MutableMap<ConfigProperty, Any?>
) {
    fun bool(key: ConfigProperty): Boolean {
        return get(key) as Boolean
    }

    fun int(key: ConfigProperty): Int {
        return get(key) as Int
    }

    fun string(key: ConfigProperty): String {
        return get(key) as String
    }

    fun double(key: ConfigProperty): Double {
        return get(key) as Double
    }

    fun float(key: ConfigProperty): Float {
        return get(key) as Float
    }

    fun long(key: ConfigProperty): Long {
        return get(key) as Long
    }

    fun short(key: ConfigProperty): Short {
        return get(key) as Short
    }

    fun byte(key: ConfigProperty): Byte {
        return get(key) as Byte
    }

    fun char(key: ConfigProperty): Char {
        return get(key) as Char
    }

    @Suppress("UNCHECKED_CAST")
    fun <T> list(key: ConfigProperty): List<T> {
        return get(key) as List<T>
    }

    fun get(key: ConfigProperty): Any? {
        return configMap[key]
    }

    fun set(key: ConfigProperty, value: Any?) {
        configMap[key] = value
    }

    fun entries(): Set<Map.Entry<ConfigProperty, Any?>> {
        return configMap.entries
    }
}
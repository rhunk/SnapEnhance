package me.rhunk.snapenhance.config.impl

import me.rhunk.snapenhance.config.ConfigValue

class ConfigStateSelection(
    private val keys: List<String>,
    var state: String = ""
) : ConfigValue<String>() {

    fun keys(): List<String> {
        return keys
    }
    override fun value(): String {
        return state
    }

    fun value(key: String) {
        state = key
    }

    override fun write(): String {
        return state
    }

    override fun read(value: String) {
        state = value
    }
}
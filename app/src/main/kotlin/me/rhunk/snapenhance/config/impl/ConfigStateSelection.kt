package me.rhunk.snapenhance.config.impl

import me.rhunk.snapenhance.config.ConfigValue

class ConfigStateSelection(
    private val keys: List<String>,
    private var state: String = ""
) : ConfigValue<String>() {
    fun keys(): List<String> {
        return keys
    }

    override fun value() = state

    override fun read(): String {
        return state
    }

    override fun write(value: String) {
        state = value
    }
}
package me.rhunk.snapenhance.config.impl

import me.rhunk.snapenhance.config.ConfigValue

class ConfigStateListValue(
    private val keys: List<String>,
    private var states: MutableMap<String, Boolean> = mutableMapOf()
) : ConfigValue<Map<String, Boolean>>() {
    override fun value() = states

    fun setKey(key: String, state: Boolean) {
        states[key] = state
        onValueChanged()
    }

    operator fun get(key: String) = states[key] ?: false

    override fun read(): String {
        return keys.joinToString("|") { "$it:${states[it]}" }
    }

    override fun write(value: String) {
        value.split("|").forEach {
            val (key, state) = it.split(":")
            states[key] = state.toBoolean()
        }
    }

    override fun toString(): String {
        return states.filter { it.value }.keys.joinToString(", ") { it }
    }
}
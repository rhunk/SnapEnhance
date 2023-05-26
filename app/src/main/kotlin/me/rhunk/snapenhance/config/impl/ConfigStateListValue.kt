package me.rhunk.snapenhance.config.impl

import me.rhunk.snapenhance.config.ConfigValue

class ConfigStateListValue(
    private val keys: List<String>,
    var states: MutableMap<String, Boolean> = mutableMapOf()
) : ConfigValue<Map<String, Boolean>>() {
    override fun value() = states

    fun value(key: String) = states[key] ?: false

    override fun write(): String {
        return keys.joinToString("|") { "$it:${states[it]}" }
    }

    override fun read(value: String) {
        value.split("|").forEach {
            val (key, state) = it.split(":")
            states[key] = state.toBoolean()
        }
    }

    override fun toString(): String {
        return states.filter { it.value }.keys.joinToString(", ") { it }
    }
}
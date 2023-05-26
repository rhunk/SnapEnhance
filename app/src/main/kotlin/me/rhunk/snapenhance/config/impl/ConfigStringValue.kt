package me.rhunk.snapenhance.config.impl

import me.rhunk.snapenhance.config.ConfigValue

class ConfigStringValue(
    var value: String = ""
) : ConfigValue<String>() {
    override fun value() = value

    override fun write(): String {
        return value
    }

    override fun read(value: String) {
        this.value = value
    }
}
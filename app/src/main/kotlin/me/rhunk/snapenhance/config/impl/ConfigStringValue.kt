package me.rhunk.snapenhance.config.impl

import me.rhunk.snapenhance.config.ConfigValue

class ConfigStringValue(
    private var value: String = ""
) : ConfigValue<String>() {
    override fun value() = value

    override fun read(): String {
        return value
    }

    override fun write(value: String) {
        this.value = value
    }
}
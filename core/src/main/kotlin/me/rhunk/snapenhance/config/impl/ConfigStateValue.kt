package me.rhunk.snapenhance.config.impl

import me.rhunk.snapenhance.config.ConfigValue

class ConfigStateValue(
    private var value: Boolean
) : ConfigValue<Boolean>() {
    override fun value() = value

    override fun read(): String {
        return value.toString()
    }

    override fun write(value: String) {
        this.value = value.toBoolean()
    }
}
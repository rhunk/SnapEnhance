package me.rhunk.snapenhance.config.impl

import me.rhunk.snapenhance.config.ConfigValue

class ConfigStateValue(
    var value: Boolean
) : ConfigValue<Boolean>() {
    override fun value() = value

    override fun write(): String {
        return value.toString()
    }

    override fun read(value: String) {
        this.value = value.toBoolean()
    }
}
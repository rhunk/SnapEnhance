package me.rhunk.snapenhance.config.impl

import me.rhunk.snapenhance.config.ConfigValue

class ConfigIntegerValue(
    var value: Int
) : ConfigValue<Int>() {
    override fun value() = value

    override fun write(): String {
        return value.toString()
    }

    override fun read(value: String) {
        this.value = value.toInt()
    }
}
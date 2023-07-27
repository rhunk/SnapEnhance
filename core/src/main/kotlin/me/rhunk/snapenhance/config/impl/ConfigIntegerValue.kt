package me.rhunk.snapenhance.config.impl

import me.rhunk.snapenhance.config.ConfigValue

class ConfigIntegerValue(
    private var value: Int
) : ConfigValue<Int>() {
    override fun value() = value

    override fun read(): String {
        return value.toString()
    }

    override fun write(value: String) {
        this.value = value.toInt()
    }
}
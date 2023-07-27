package me.rhunk.snapenhance.config.impl

import me.rhunk.snapenhance.config.ConfigValue

class ConfigStringValue(
    private var value: String = "",
    val isFolderPath: Boolean = false,
    val isHidden: Boolean = false
) : ConfigValue<String>() {
    override fun value() = value

    fun hiddenValue() = if (isHidden) value.map { '*' }.joinToString("") else value

    override fun read(): String {
        return value
    }

    override fun write(value: String) {
        this.value = value
    }
}
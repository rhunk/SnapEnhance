package me.rhunk.snapenhance.common.scripting

fun Any?.toPrimitiveValue(type: Lazy<String>) = when (this) {
    is Number -> when (type.value) {
        "byte" -> this.toByte()
        "short" -> this.toShort()
        "int" -> this.toInt()
        "long" -> this.toLong()
        "float" -> this.toFloat()
        "double" -> this.toDouble()
        "boolean" -> this.toByte() != 0.toByte()
        "char" -> this.toInt().toChar()
        else -> this
    }
    is Boolean -> if (type.value == "boolean") this.toString().toBoolean() else this
    else -> this
}
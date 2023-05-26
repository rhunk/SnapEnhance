package me.rhunk.snapenhance.config

abstract class ConfigValue<T> {
    abstract fun value(): T
    abstract fun write(): String
    abstract fun read(value: String)
}
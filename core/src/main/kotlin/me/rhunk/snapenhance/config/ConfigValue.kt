package me.rhunk.snapenhance.config

abstract class ConfigValue<T> {
    private val propertyChangeListeners = mutableListOf<(T) -> Unit>()

    fun addPropertyChangeListener(listener: (T) -> Unit) = propertyChangeListeners.add(listener)
    fun removePropertyChangeListener(listener: (T) -> Unit) = propertyChangeListeners.remove(listener)

    abstract fun value(): T
    abstract fun read(): String
    protected abstract fun write(value: String)

    protected fun onValueChanged() {
        propertyChangeListeners.forEach { it(value()) }
    }

    fun writeFrom(value: String) {
        val oldValue = read()
        write(value)
        if (oldValue != value) onValueChanged()
    }
}
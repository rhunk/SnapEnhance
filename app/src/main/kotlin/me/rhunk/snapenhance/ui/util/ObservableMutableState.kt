package me.rhunk.snapenhance.ui.util

import androidx.compose.runtime.MutableState

class ObservableMutableState<T>(
    defaultValue: T,
    inline val onChange: (T, T) -> Unit = { _, _ -> },
) : MutableState<T> {
    private var mutableValue: T = defaultValue
    override var value: T
        get() = mutableValue
        set(value) {
            val oldValue = mutableValue
            mutableValue = value
            onChange(oldValue, value)
        }
    override fun component1() = value
    override fun component2(): (T) -> Unit = { value = it }
}
package me.rhunk.snapenhance.common.scripting.bindings

abstract class AbstractBinding(
    val name: String,
    val side: BindingSide
) {
    lateinit var context: BindingsContext

    open fun onInit() {}

    open fun onDispose() {}

    abstract fun getObject(): Any
}
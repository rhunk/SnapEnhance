package me.rhunk.snapenhance.scripting

typealias Listener = (Array<out String?>) -> Unit

abstract class IPCInterface {
    abstract fun on(eventName: String, listener: Listener)

    abstract fun emit(eventName: String, vararg args: String?)

    @Suppress("unused")
    fun emit(eventName: String) = emit(eventName, *emptyArray())
}
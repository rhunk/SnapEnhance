package me.rhunk.snapenhance.common.scripting.impl

typealias Listener = (Array<out String?>) -> Unit

abstract class IPCInterface {
    abstract fun on(eventName: String, listener: Listener)

    abstract fun onBroadcast(channel: String, eventName: String, listener: Listener)

    abstract fun emit(eventName: String, vararg args: String?)
    abstract fun broadcast(channel: String, eventName: String, vararg args: String?)

    @Suppress("unused")
    fun emit(eventName: String) = emit(eventName, *emptyArray())
    @Suppress("unused")
    fun emit(channel: String, eventName: String) = broadcast(channel, eventName)
}
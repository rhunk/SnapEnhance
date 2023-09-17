package me.rhunk.snapenhance.scripting

typealias Listener = (Array<out String?>) -> Unit

interface IPCInterface {
    fun on(eventName: String, listener: Listener)
    fun emit(eventName: String, args: Array<out String?>)
}
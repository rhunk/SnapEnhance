package me.rhunk.snapenhance.scripting

import java.util.concurrent.ConcurrentHashMap

class IRemoteIPC : IPCInterface() {
    private val listeners = ConcurrentHashMap<String, MutableSet<Listener>>()

    fun removeListener(eventName: String, listener: Listener) {
        listeners[eventName]?.remove(listener)
    }

    override fun on(eventName: String, listener: Listener) {
        listeners.getOrPut(eventName) { mutableSetOf() }.add(listener)
    }

    override fun emit(eventName: String, args: Array<out String?>) {
        listeners[eventName]?.toList()?.forEach { it(args) }
    }
}
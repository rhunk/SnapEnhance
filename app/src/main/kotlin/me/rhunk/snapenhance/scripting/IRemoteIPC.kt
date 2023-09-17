package me.rhunk.snapenhance.scripting

class IRemoteIPC : IPCInterface {
    private val listeners = mutableMapOf<String, MutableSet<Listener>>()

    override fun on(eventName: String, listener: Listener) {
        listeners.getOrPut(eventName) { mutableSetOf() }.add(listener)
    }

    override fun emit(eventName: String, args: Array<out String?>) {
        listeners[eventName]?.forEach { it(args) }
    }
}
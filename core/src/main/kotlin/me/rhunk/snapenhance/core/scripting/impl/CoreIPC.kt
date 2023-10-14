package me.rhunk.snapenhance.core.scripting.impl

import me.rhunk.snapenhance.bridge.scripting.IPCListener
import me.rhunk.snapenhance.bridge.scripting.IScripting
import me.rhunk.snapenhance.common.scripting.impl.IPCInterface
import me.rhunk.snapenhance.common.scripting.impl.Listener
import me.rhunk.snapenhance.common.scripting.type.ModuleInfo

class CoreIPC(
    private val scripting: IScripting,
    private val moduleInfo: ModuleInfo
) : IPCInterface() {
    override fun onBroadcast(channel: String, eventName: String, listener: Listener) {
        scripting.registerIPCListener(channel, eventName, object: IPCListener.Stub() {
            override fun onMessage(args: Array<out String?>) {
                listener(args)
            }
        })
    }

    override fun on(eventName: String, listener: Listener) {
        onBroadcast(moduleInfo.name, eventName, listener)
    }

    override fun emit(eventName: String, vararg args: String?) {
        broadcast(moduleInfo.name, eventName, *args)
    }

    override fun broadcast(channel: String, eventName: String, vararg args: String?) {
        scripting.sendIPCMessage(channel, eventName, args)
    }
}
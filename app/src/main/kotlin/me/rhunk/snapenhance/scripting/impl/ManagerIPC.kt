package me.rhunk.snapenhance.scripting.impl

import android.os.DeadObjectException
import me.rhunk.snapenhance.bridge.scripting.IPCListener
import me.rhunk.snapenhance.common.scripting.impl.IPCInterface
import me.rhunk.snapenhance.common.scripting.impl.Listener
import java.util.concurrent.ConcurrentHashMap

typealias IPCListeners = ConcurrentHashMap<String, MutableMap<String, MutableSet<IPCListener>>>  // channel, eventName -> listeners

class ManagerIPC(
    private val ipcListeners: IPCListeners = ConcurrentHashMap(),
) : IPCInterface() {
    companion object {
        private const val TAG = "RemoteManagerIPC"
    }

    override fun on(eventName: String, listener: Listener) {
        onBroadcast(context.moduleInfo.name, eventName, listener)
    }

    override fun emit(eventName: String, vararg args: String?) {
        emit(context.moduleInfo.name, eventName, *args)
    }

    override fun onBroadcast(channel: String, eventName: String, listener: Listener) {
        ipcListeners.getOrPut(channel) { mutableMapOf() }.getOrPut(eventName) { mutableSetOf() }.add(object: IPCListener.Stub() {
            override fun onMessage(args: Array<out String?>) {
                try {
                    listener(args.toList())
                } catch (doe: DeadObjectException) {
                    ipcListeners[channel]?.get(eventName)?.remove(this)
                } catch (t: Throwable) {
                    context.runtime.logger.error("Failed to receive message for channel: $channel, event: $eventName", t, TAG)
                }
            }
        })
    }

    override fun broadcast(channel: String, eventName: String, vararg args: String?) {
        ipcListeners[channel]?.get(eventName)?.toList()?.forEach {
            try {
                it.onMessage(args)
            } catch (doe: DeadObjectException) {
                ipcListeners[channel]?.get(eventName)?.remove(it)
            } catch (t: Throwable) {
                context.runtime.logger.error("Failed to send message for channel: $channel, event: $eventName", t, TAG)
            }
        }
    }
}
package me.rhunk.snapenhance.scripting.core

import android.content.Context
import me.rhunk.snapenhance.bridge.scripting.IPCListener
import me.rhunk.snapenhance.bridge.scripting.IScripting
import me.rhunk.snapenhance.core.logger.AbstractLogger
import me.rhunk.snapenhance.scripting.IPCInterface
import me.rhunk.snapenhance.scripting.Listener
import me.rhunk.snapenhance.scripting.ScriptRuntime
import me.rhunk.snapenhance.scripting.core.impl.ScriptHooker

class CoreScriptRuntime(
    androidContext: Context,
    logger: AbstractLogger,
): ScriptRuntime(androidContext, logger) {
    private val scriptHookers = mutableListOf<ScriptHooker>()

    fun connect(scriptingInterface: IScripting) {
        scriptingInterface.apply {
            buildModuleObject = { module ->
                putConst("ipc", this, object: IPCInterface() {
                    override fun onBroadcast(channel: String, eventName: String, listener: Listener) {
                        registerIPCListener(channel, eventName, object: IPCListener.Stub() {
                            override fun onMessage(args: Array<out String?>) {
                                listener(args)
                            }
                        })
                    }

                    override fun on(eventName: String, listener: Listener) {
                        onBroadcast(module.moduleInfo.name, eventName, listener)
                    }

                    override fun emit(eventName: String, vararg args: String?) {
                        broadcast(module.moduleInfo.name, eventName, *args)
                    }

                    override fun broadcast(channel: String, eventName: String, vararg args: String?) {
                        sendIPCMessage(channel, eventName, args)
                    }
                })
                putConst("hooker", this, ScriptHooker(module.moduleInfo, logger, androidContext.classLoader).also {
                    scriptHookers.add(it)
                })
            }
        }

        scriptingInterface.enabledScripts.forEach { path ->
            runCatching {
                load(path, scriptingInterface.getScriptContent(path))
            }.onFailure {
                logger.error("Failed to load script $path", it)
            }
        }
    }
}
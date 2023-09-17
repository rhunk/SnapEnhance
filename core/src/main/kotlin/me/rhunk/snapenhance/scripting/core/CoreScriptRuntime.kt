package me.rhunk.snapenhance.scripting.core

import me.rhunk.snapenhance.bridge.scripting.IPCListener
import me.rhunk.snapenhance.bridge.scripting.IScripting
import me.rhunk.snapenhance.bridge.scripting.ReloadListener
import me.rhunk.snapenhance.core.logger.AbstractLogger
import me.rhunk.snapenhance.scripting.IPCInterface
import me.rhunk.snapenhance.scripting.Listener
import me.rhunk.snapenhance.scripting.ScriptRuntime
import me.rhunk.snapenhance.scripting.core.impl.ScriptHooker
import me.rhunk.snapenhance.scripting.ktx.putFunction

class CoreScriptRuntime(
    logger: AbstractLogger,
    private val classLoader: ClassLoader
): ScriptRuntime(logger) {
    private lateinit var ipcInterface: IPCInterface

    private val scriptHookers = mutableListOf<ScriptHooker>()

    fun connect(scriptingInterface: IScripting) {
        scriptingInterface.apply {
            registerReloadListener(object: ReloadListener.Stub() {
                override fun reloadScript(path: String, content: String) {
                    reload(path, content)
                }
            })

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
                putFunction("findClass") {
                    val className = it?.get(0).toString()
                    classLoader.loadClass(className)
                }
                putConst("hooker", this, ScriptHooker(module.moduleInfo, logger, classLoader).also {
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
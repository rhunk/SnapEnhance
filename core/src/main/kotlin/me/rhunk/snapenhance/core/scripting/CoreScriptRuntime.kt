package me.rhunk.snapenhance.core.scripting

import me.rhunk.snapenhance.bridge.scripting.AutoReloadListener
import me.rhunk.snapenhance.bridge.scripting.IScripting
import me.rhunk.snapenhance.common.logger.AbstractLogger
import me.rhunk.snapenhance.common.scripting.ScriptRuntime
import me.rhunk.snapenhance.core.ModContext
import me.rhunk.snapenhance.core.scripting.impl.CoreIPC
import me.rhunk.snapenhance.core.scripting.impl.CoreScriptConfig
import me.rhunk.snapenhance.core.scripting.impl.ScriptHooker

class CoreScriptRuntime(
    private val modContext: ModContext,
    logger: AbstractLogger,
): ScriptRuntime(modContext.androidContext, logger) {
    private val scriptHookers = mutableListOf<ScriptHooker>()

    fun connect(scriptingInterface: IScripting) {
        scriptingInterface.apply {
            buildModuleObject = { module ->
                module.extras["ipc"] = CoreIPC(this@apply, module.moduleInfo)
                module.extras["hooker"] = ScriptHooker(module.moduleInfo, logger, androidContext.classLoader).also {
                    scriptHookers.add(it)
                }
                module.extras["config"] = CoreScriptConfig(this@apply, module.moduleInfo)
            }

            enabledScripts.forEach { path ->
                runCatching {
                    load(path, scriptingInterface.getScriptContent(path))
                }.onFailure {
                    logger.error("Failed to load script $path", it)
                }
            }

            registerAutoReloadListener(object : AutoReloadListener.Stub() {
                override fun restartApp() {
                    modContext.softRestartApp()
                }
            })
        }
    }
}
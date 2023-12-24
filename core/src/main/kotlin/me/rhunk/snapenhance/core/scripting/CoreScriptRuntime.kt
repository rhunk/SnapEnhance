package me.rhunk.snapenhance.core.scripting

import me.rhunk.snapenhance.bridge.scripting.AutoReloadListener
import me.rhunk.snapenhance.bridge.scripting.IScripting
import me.rhunk.snapenhance.common.logger.AbstractLogger
import me.rhunk.snapenhance.common.scripting.ScriptRuntime
import me.rhunk.snapenhance.core.ModContext
import me.rhunk.snapenhance.core.scripting.impl.CoreIPC
import me.rhunk.snapenhance.core.scripting.impl.CoreScriptConfig
import me.rhunk.snapenhance.core.scripting.impl.CoreScriptHooker

class CoreScriptRuntime(
    private val modContext: ModContext,
    logger: AbstractLogger,
): ScriptRuntime(modContext.androidContext, logger) {
    fun connect(scriptingInterface: IScripting) {
        scripting = scriptingInterface
        scriptingInterface.apply {
            buildModuleObject = { module ->
                module.registerBindings(
                    CoreScriptConfig(),
                    CoreIPC(),
                    CoreScriptHooker(),
                )
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
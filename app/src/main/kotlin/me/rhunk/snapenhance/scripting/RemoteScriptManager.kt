package me.rhunk.snapenhance.scripting

import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import me.rhunk.snapenhance.RemoteSideContext
import me.rhunk.snapenhance.bridge.scripting.AutoReloadListener
import me.rhunk.snapenhance.bridge.scripting.IPCListener
import me.rhunk.snapenhance.bridge.scripting.IScripting
import me.rhunk.snapenhance.common.scripting.ScriptRuntime
import me.rhunk.snapenhance.common.scripting.impl.ConfigInterface
import me.rhunk.snapenhance.common.scripting.impl.ConfigTransactionType
import me.rhunk.snapenhance.common.scripting.type.ModuleInfo
import me.rhunk.snapenhance.scripting.impl.IPCListeners
import me.rhunk.snapenhance.scripting.impl.RemoteManagerIPC
import me.rhunk.snapenhance.scripting.impl.RemoteScriptConfig
import me.rhunk.snapenhance.scripting.impl.ui.InterfaceManager
import java.io.File
import java.io.InputStream
import kotlin.system.exitProcess

class RemoteScriptManager(
    val context: RemoteSideContext,
) : IScripting.Stub() {
    val runtime = ScriptRuntime(context.androidContext, context.log)

    private var autoReloadListener: AutoReloadListener? = null
    private val autoReloadHandler by lazy {
        AutoReloadHandler(context.coroutineScope) {
            runCatching {
                autoReloadListener?.restartApp()
                if (context.config.root.scripting.autoReload.getNullable() == "all") {
                    exitProcess(1)
                }
            }.onFailure {
                context.log.warn("Failed to restart app")
                autoReloadListener = null
            }
        }.apply {
            start()
        }
    }

    private val cachedModuleInfo = mutableMapOf<String, ModuleInfo>()
    private val ipcListeners = IPCListeners()

    fun sync() {
        getScriptFileNames().forEach { name ->
            runCatching {
                getScriptInputStream(name) { stream ->
                    runtime.getModuleInfo(stream!!).also { info ->
                        cachedModuleInfo[name] = info
                    }
                }
            }.onFailure {
                context.log.error("Failed to load module info for $name", it)
            }
        }

        context.modDatabase.syncScripts(cachedModuleInfo.values.toList())
    }

    fun init() {
        runtime.buildModuleObject = { module ->
            module.extras["ipc"] = RemoteManagerIPC(module.moduleInfo, context.log, ipcListeners)
            module.extras["im"] = InterfaceManager(module.moduleInfo, context.log)
            module.extras["config"] = RemoteScriptConfig(this@RemoteScriptManager, module.moduleInfo, context.log).also {
                it.load()
            }
        }

        sync()
        enabledScripts.forEach { name ->
            loadScript(name)
        }
    }

    fun loadScript(name: String) {
        val content = getScriptContent(name) ?: return
        if (context.config.root.scripting.autoReload.getNullable() != null) {
            autoReloadHandler.addFile(getScriptsFolder()?.findFile(name) ?: return)
        }
        runtime.load(name, content)
    }

    private fun <R> getScriptInputStream(name: String, callback: (InputStream?) -> R): R {
        val file = getScriptsFolder()?.findFile(name) ?: return callback(null)
        return context.androidContext.contentResolver.openInputStream(file.uri)?.use(callback) ?: callback(null)
    }

    fun getModuleDataFolder(moduleFileName: String): File {
        return context.androidContext.filesDir.resolve("modules").resolve(moduleFileName).also {
            if (!it.exists()) {
                it.mkdirs()
            }
        }
    }

    fun getScriptsFolder() = runCatching {
        DocumentFile.fromTreeUri(context.androidContext, Uri.parse(context.config.root.scripting.moduleFolder.get()))
    }.onFailure {
        context.log.warn("Failed to get scripts folder")
    }.getOrNull()

    private fun getScriptFileNames(): List<String> {
        return (getScriptsFolder() ?: return emptyList()).listFiles().filter { it.name?.endsWith(".js") ?: false }.map { it.name!! }
    }

    override fun getEnabledScripts(): List<String> {
        return runCatching {
            getScriptFileNames().filter {
                context.modDatabase.isScriptEnabled(cachedModuleInfo[it]?.name ?: return@filter false)
            }
        }.onFailure {
            context.log.error("Failed to get enabled scripts", it)
        }.getOrDefault(emptyList())
    }

    override fun getScriptContent(moduleName: String): String? {
        return getScriptInputStream(moduleName) { it?.bufferedReader()?.readText() }
    }

    override fun registerIPCListener(channel: String, eventName: String, listener: IPCListener) {
        ipcListeners.getOrPut(channel) { mutableMapOf() }.getOrPut(eventName) { mutableSetOf() }.add(listener)
    }

    override fun sendIPCMessage(channel: String, eventName: String, args: Array<out String>) {
        runCatching {
            ipcListeners[channel]?.get(eventName)?.toList()?.forEach {
                it.onMessage(args)
            }
        }.onFailure {
            context.log.error("Failed to send message for $eventName", it)
        }
    }

    override fun configTransaction(
        module: String?,
        action: String,
        key: String?,
        value: String?,
        save: Boolean
    ): String? {
        val scriptConfig = runtime.getModuleByName(module ?: return null)?.extras?.get("config") as? ConfigInterface ?: return null.also {
            context.log.warn("Failed to get config interface for $module")
        }
        val transactionType = ConfigTransactionType.fromKey(action)

        return runCatching {
            scriptConfig.run {
                if (transactionType == ConfigTransactionType.GET) {
                    return get(key ?: return@runCatching null, value)
                }
                when (transactionType) {
                    ConfigTransactionType.SET -> set(key ?: return@runCatching null, value, save)
                    ConfigTransactionType.SAVE -> save()
                    ConfigTransactionType.LOAD -> load()
                    ConfigTransactionType.DELETE -> delete()
                    else -> {}
                }
                null
            }
        }.onFailure {
            context.log.error("Failed to perform config transaction", it)
        }.getOrDefault("")
    }

    override fun registerAutoReloadListener(listener: AutoReloadListener?) {
        autoReloadListener = listener
    }
}
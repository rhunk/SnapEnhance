package me.rhunk.snapenhance.scripting

import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import me.rhunk.snapenhance.RemoteSideContext
import me.rhunk.snapenhance.bridge.scripting.IPCListener
import me.rhunk.snapenhance.bridge.scripting.IScripting
import me.rhunk.snapenhance.scripting.type.ModuleInfo
import java.io.InputStream

class RemoteScriptManager(
    private val context: RemoteSideContext,
) : IScripting.Stub() {
    val runtime = ScriptRuntime(context.log, context.androidContext.classLoader)

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
            putConst("ipc", this, RemoteManagerIPC(module.moduleInfo, context.log, ipcListeners))
        }

        sync()
        enabledScripts.forEach { name ->
            if (getModuleDataFolder(name) == null) {
                context.log.warn("Module data folder not found for $name")
            }
            val content = getScriptContent(name) ?: return@forEach
            runtime.load(name, content)
        }
    }

    private fun <R> getScriptInputStream(name: String, callback: (InputStream?) -> R): R {
        val file = getScriptsFolder()?.findFile(name) ?: return callback(null)
        return context.androidContext.contentResolver.openInputStream(file.uri)?.use(callback) ?: callback(null)
    }

    private fun getModuleDataFolder(moduleFileName: String): DocumentFile? {
        val folderName = moduleFileName.substringBeforeLast(".js")
        val folder = getScriptsFolder() ?: return null
        return folder.findFile(folderName) ?: folder.createDirectory(folderName)
    }

    private fun getScriptsFolder() = DocumentFile.fromTreeUri(context.androidContext, Uri.parse(context.config.root.scripting.moduleFolder.get()))

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
}
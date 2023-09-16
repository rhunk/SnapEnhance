package me.rhunk.snapenhance.scripting

import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import me.rhunk.snapenhance.RemoteSideContext
import me.rhunk.snapenhance.bridge.scripting.IScripting
import me.rhunk.snapenhance.bridge.scripting.ReloadListener
import me.rhunk.snapenhance.scripting.type.ModuleInfo
import java.io.InputStream

class RemoteScriptManager(
    private val context: RemoteSideContext,
) : IScripting.Stub() {
    val runtime = ScriptRuntime(context.log)

    private fun getScriptFolder()
        = DocumentFile.fromTreeUri(context.androidContext, Uri.parse(context.config.root.scripting.moduleFolder.get()))
    //private fun hasHotReload() = context.config.root.scripting.hotReload.get()
    private val reloadListeners = mutableListOf<ReloadListener>()

    private val cachedModuleInfo = mutableMapOf<String, ModuleInfo>()

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
        sync()

        enabledScripts.forEach { path ->
            val content = getScriptContent(path) ?: return@forEach
            runtime.load(path, content)
        }
    }

    private fun <R> getScriptInputStream(name: String, callback: (InputStream?) -> R): R {
        val file = getScriptFolder()?.findFile(name) ?: return callback(null)
        return context.androidContext.contentResolver.openInputStream(file.uri)?.use(callback) ?: callback(null)
    }

    private fun getScriptFileNames(): List<String> {
        return (getScriptFolder() ?: return emptyList()).listFiles().filter { it.name?.endsWith(".js") ?: false }.map { it.name!! }
    }

    override fun getEnabledScripts(): List<String> {
        return getScriptFileNames().filter {
            context.modDatabase.isScriptEnabled(cachedModuleInfo[it]?.name ?: return@filter false)
        }
    }

    override fun getScriptContent(name: String): String? {
        return getScriptInputStream(name) { it?.bufferedReader()?.readText() }
    }

    override fun registerReloadListener(listener: ReloadListener) {
        reloadListeners.add(listener)
    }
}
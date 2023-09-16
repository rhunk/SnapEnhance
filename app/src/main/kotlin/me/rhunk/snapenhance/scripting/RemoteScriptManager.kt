package me.rhunk.snapenhance.scripting

import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import me.rhunk.snapenhance.RemoteSideContext
import me.rhunk.snapenhance.bridge.scripting.IScripting
import me.rhunk.snapenhance.bridge.scripting.ReloadListener

class RemoteScriptManager(
    private val context: RemoteSideContext,
) : IScripting.Stub() {
    private val scriptRuntime = ScriptRuntime(context.log)

    private fun getScriptFolder()
        = DocumentFile.fromTreeUri(context.androidContext, Uri.parse(context.config.root.scripting.moduleFolder.get()))
    private fun hasHotReload() = context.config.root.scripting.hotReload.get()

    private val reloadListeners = mutableListOf<ReloadListener>()

    fun init() {
        enabledScriptPaths.forEach { path ->
            val content = getScriptContent(path)
            scriptRuntime.load(path, content)
        }
    }

    override fun getEnabledScriptPaths(): List<String> {
        val folder = getScriptFolder() ?: return emptyList()
        return folder.listFiles().filter { it.name?.endsWith(".js") ?: false }.map { it.name!! }
    }

    override fun getScriptContent(path: String): String {
        val folder = getScriptFolder() ?: return ""
        val file = folder.findFile(path) ?: return ""
        return context.androidContext.contentResolver.openInputStream(file.uri)?.use {
            it.readBytes().toString(Charsets.UTF_8)
        } ?: ""
    }

    override fun registerReloadListener(listener: ReloadListener) {
        reloadListeners.add(listener)
    }
}
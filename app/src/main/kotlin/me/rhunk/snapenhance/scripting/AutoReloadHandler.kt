package me.rhunk.snapenhance.scripting

import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class AutoReloadHandler(
    private val coroutineScope: CoroutineScope,
    private val onReload: (DocumentFile) -> Unit,
) {
    private val files = mutableListOf<DocumentFile>()
    private val lastModifiedMap = mutableMapOf<Uri, Long>()

    fun addFile(file: DocumentFile) {
        files.add(file)
        lastModifiedMap[file.uri] = file.lastModified()
    }

    fun start() {
        coroutineScope.launch(Dispatchers.IO) {
            while (true) {
                files.forEach { file ->
                    val lastModified = lastModifiedMap[file.uri] ?: return@forEach
                    runCatching {
                        val newLastModified = file.lastModified()
                        if (newLastModified > lastModified) {
                            lastModifiedMap[file.uri] = newLastModified
                            onReload(file)
                        }
                    }.onFailure {
                        it.printStackTrace()
                    }
                }
                delay(1000)
            }
        }
    }
}
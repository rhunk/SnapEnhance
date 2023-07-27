package me.rhunk.snapenhance.action.impl

import me.rhunk.snapenhance.action.AbstractAction
import java.io.File

class CleanCache : AbstractAction("action.clean_cache") {
    companion object {
        private val FILES = arrayOf(
            "files/mbgl-offline.db",
            "files/native_content_manager/*",
            "files/file_manager/*",
            "files/blizzardv2/*",
            "files/streaming/*",
            "cache/*",
            "databases/media_packages",
            "databases/simple_db_helper.db",
            "databases/journal.db",
            "databases/arroyo.db",
            "databases/arroyo.db-wal",
            "databases/native_content_manager/*"
        )
    }

    override fun run() {
        FILES.forEach {fileName ->
            val fileCache = File(context.androidContext.dataDir, fileName)
            if (fileName.endsWith("*")) {
                val parent = fileCache.parentFile ?: throw IllegalStateException("Parent file is null")
                if (parent.exists()) {
                    parent.listFiles()?.forEach(this::deleteRecursively)
                }
                return@forEach
            }
            if (fileCache.exists()) {
                deleteRecursively(fileCache)
            }
        }

        context.softRestartApp()
    }
}
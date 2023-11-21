package me.rhunk.snapenhance.core.action.impl

import me.rhunk.snapenhance.core.action.AbstractAction
import java.io.File

class CleanCache : AbstractAction() {
    companion object {
        private val FILES = arrayOf(
            "files/mbgl-offline.db",
            "files/native_content_manager/*",
            "files/file_manager/*",
            "files/composer_cache/*",
            "files/blizzardv2/*",
            "files/streaming/*",
            "cache/*",
            "files/streaming/*",
            "databases/media_packages",
            "databases/simple_db_helper.db",
            "databases/simple_db_helper.db-wal",
            "databases/simple_db_helper.db-shm",
            "databases/journal.db",
            "databases/arroyo.db",
            "databases/arroyo.db-wal",
            "databases/arroyo.db-shm",
            "databases/native_content_manager/*"
        )
    }

    override fun run() {
        FILES.forEach { fileName ->
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
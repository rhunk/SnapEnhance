package me.rhunk.snapenhance.download

import android.annotation.SuppressLint
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import me.rhunk.snapenhance.download.data.DownloadMetadata
import me.rhunk.snapenhance.download.data.PendingDownload
import me.rhunk.snapenhance.download.enums.DownloadStage
import me.rhunk.snapenhance.ui.download.MediaFilter
import me.rhunk.snapenhance.util.SQLiteDatabaseHelper

class DownloadTaskManager {
    private lateinit var taskDatabase: SQLiteDatabase
    private val pendingTasks = mutableMapOf<Int, PendingDownload>()
    private val cachedTasks = mutableMapOf<Int, PendingDownload>()

    @SuppressLint("Range")
    fun init(context: Context) {
        if (this::taskDatabase.isInitialized) return
        taskDatabase = context.openOrCreateDatabase("download_tasks", Context.MODE_PRIVATE, null).apply {
            SQLiteDatabaseHelper.createTablesFromSchema(this, mapOf(
                "tasks" to listOf(
                    "id INTEGER PRIMARY KEY AUTOINCREMENT",
                    "hash VARCHAR UNIQUE",
                    "outputPath TEXT",
                    "outputFile TEXT",
                    "mediaDisplayType TEXT",
                    "mediaDisplaySource TEXT",
                    "iconUrl TEXT",
                    "downloadStage TEXT"
                )
            ))
        }
    }

    fun addTask(task: PendingDownload): Int {
        taskDatabase.execSQL("INSERT INTO tasks (hash, outputPath, outputFile, mediaDisplayType, mediaDisplaySource, iconUrl, downloadStage) VALUES (?, ?, ?, ?, ?, ?, ?)",
            arrayOf(
                task.metadata.mediaIdentifier,
                task.metadata.outputPath,
                task.outputFile,
                task.metadata.mediaDisplayType,
                task.metadata.mediaDisplaySource,
                task.metadata.iconUrl,
                task.downloadStage.name
            )
        )
        task.downloadId = taskDatabase.rawQuery("SELECT last_insert_rowid()", null).use {
            it.moveToFirst()
            it.getInt(0)
        }
        pendingTasks[task.downloadId] = task
        return task.downloadId
    }

    fun updateTask(task: PendingDownload) {
        taskDatabase.execSQL("UPDATE tasks SET hash = ?, outputPath = ?, outputFile = ?, mediaDisplayType = ?, mediaDisplaySource = ?, iconUrl = ?, downloadStage = ? WHERE id = ?",
            arrayOf(
                task.metadata.mediaIdentifier,
                task.metadata.outputPath,
                task.outputFile,
                task.metadata.mediaDisplayType,
                task.metadata.mediaDisplaySource,
                task.metadata.iconUrl,
                task.downloadStage.name,
                task.downloadId
            )
        )
        //if the task is no longer active, move it to the cached tasks
        if (task.isJobActive()) {
            pendingTasks[task.downloadId] = task
        } else {
            pendingTasks.remove(task.downloadId)
            cachedTasks[task.downloadId] = task
        }
    }

    @SuppressLint("Range")
    fun canDownloadMedia(mediaIdentifier: String?): DownloadStage? {
        if (mediaIdentifier == null) return null

        val cursor = taskDatabase.rawQuery("SELECT * FROM tasks WHERE hash = ?", arrayOf(mediaIdentifier))
        if (cursor.count > 0) {
            cursor.moveToFirst()
            val downloadStage = DownloadStage.valueOf(cursor.getString(cursor.getColumnIndex("downloadStage")))
            cursor.close()

            //if the stage has reached a final stage and is not in a saved state, remove the task
            if (downloadStage.isFinalStage && downloadStage != DownloadStage.SAVED) {
                taskDatabase.execSQL("DELETE FROM tasks WHERE hash = ?", arrayOf(mediaIdentifier))
                return null
            }

            return downloadStage
        }
        cursor.close()
        return null
    }

    fun isEmpty(): Boolean {
        return cachedTasks.isEmpty() && pendingTasks.isEmpty()
    }

    private fun removeTask(id: Int) {
        taskDatabase.execSQL("DELETE FROM tasks WHERE id = ?", arrayOf(id))
        cachedTasks.remove(id)
        pendingTasks.remove(id)
    }

    fun removeTask(task: PendingDownload) {
        removeTask(task.downloadId)
    }

    fun queryAllTasks(filter: MediaFilter): Map<Int, PendingDownload> {
        val isPendingFilter = filter == MediaFilter.PENDING
        val tasks = mutableMapOf<Int, PendingDownload>()

        tasks.putAll(pendingTasks.filter { isPendingFilter || filter.matches(it.value.metadata.mediaDisplayType) })
        if (isPendingFilter) {
            return tasks.toSortedMap(reverseOrder())
        }

        tasks.putAll(queryTasks(
            from = tasks.values.lastOrNull()?.downloadId ?: Int.MAX_VALUE,
            amount = 30,
            filter = filter
        ))

        return tasks.toSortedMap(reverseOrder())
    }

    @SuppressLint("Range")
    fun queryTasks(from: Int, amount: Int = 20, filter: MediaFilter = MediaFilter.NONE): Map<Int, PendingDownload> {
        if (filter == MediaFilter.PENDING) {
            return emptyMap()
        }

        val cursor = taskDatabase.rawQuery(
            "SELECT * FROM tasks WHERE id < ? AND mediaDisplayType LIKE ? ORDER BY id DESC LIMIT ?",
            arrayOf(
                from.toString(),
                filter.mediaDisplayType.let { if (it == null) "%" else "%$it" },
                amount.toString()
            )
        )

        val result = sortedMapOf<Int, PendingDownload>()

        while (cursor.moveToNext()) {
            val task = PendingDownload(
                downloadId = cursor.getInt(cursor.getColumnIndex("id")),
                outputFile = cursor.getString(cursor.getColumnIndex("outputFile")),
                metadata = DownloadMetadata(
                    outputPath = cursor.getString(cursor.getColumnIndex("outputPath")),
                    mediaIdentifier = cursor.getString(cursor.getColumnIndex("hash")),
                    mediaDisplayType = cursor.getString(cursor.getColumnIndex("mediaDisplayType")),
                    mediaDisplaySource = cursor.getString(cursor.getColumnIndex("mediaDisplaySource")),
                    iconUrl = cursor.getString(cursor.getColumnIndex("iconUrl"))
                )
            ).apply {
                downloadStage = DownloadStage.valueOf(cursor.getString(cursor.getColumnIndex("downloadStage")))
                //if downloadStage is not saved, it means the app was killed before the download was finished
                if (downloadStage != DownloadStage.SAVED) {
                    downloadStage = DownloadStage.FAILED
                }
            }
            result[task.downloadId] = task
        }
        cursor.close()

        return result.toSortedMap(reverseOrder())
    }

    fun removeAllTasks() {
        taskDatabase.execSQL("DELETE FROM tasks")
        cachedTasks.clear()
        pendingTasks.clear()
    }
}
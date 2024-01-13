package me.rhunk.snapenhance.task

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import me.rhunk.snapenhance.RemoteSideContext
import me.rhunk.snapenhance.common.util.SQLiteDatabaseHelper
import me.rhunk.snapenhance.common.util.ktx.getLong
import me.rhunk.snapenhance.common.util.ktx.getStringOrNull
import java.util.concurrent.Executors
import kotlin.coroutines.suspendCoroutine

class TaskManager(
    private val remoteSideContext: RemoteSideContext
) {
    private lateinit var taskDatabase: SQLiteDatabase
    private val queueExecutor = Executors.newSingleThreadExecutor()

    fun init() {
        taskDatabase = remoteSideContext.androidContext.openOrCreateDatabase("tasks", Context.MODE_PRIVATE, null).apply {
            SQLiteDatabaseHelper.createTablesFromSchema(this, mapOf(
                "tasks" to listOf(
                    "id INTEGER PRIMARY KEY AUTOINCREMENT",
                    "hash VARCHAR UNIQUE",
                    "title VARCHAR(255) NOT NULL",
                    "author VARCHAR(255)",
                    "type VARCHAR(255) NOT NULL",
                    "status VARCHAR(255) NOT NULL",
                    "extra TEXT"
                )
            ))
        }
    }

    private val activeTasks = mutableMapOf<Long, PendingTask>()

    private fun readTaskFromCursor(cursor: android.database.Cursor): Task {
        val task = Task(
            type = TaskType.fromKey(cursor.getStringOrNull("type")!!),
            title = cursor.getStringOrNull("title")!!,
            author = cursor.getStringOrNull("author"),
            hash = cursor.getStringOrNull("hash")!!
        )
        task.status = TaskStatus.fromKey(cursor.getStringOrNull("status")!!)
        task.extra = cursor.getStringOrNull("extra")
        task.changeListener = {
            updateTask(cursor.getLong("id"), task)
        }
        return task
    }

    private fun putNewTask(task: Task): Long {
        return runBlocking {
            suspendCoroutine {
                queueExecutor.execute {
                    taskDatabase.rawQuery("SELECT * FROM tasks WHERE hash = ?", arrayOf(task.hash)).use { cursor ->
                        if (cursor.moveToNext()) {
                            it.resumeWith(Result.success(cursor.getLong("id")))
                            return@execute
                        }
                    }

                    val result = taskDatabase.insert("tasks", null, ContentValues().apply {
                        put("type", task.type.key)
                        put("hash", task.hash)
                        put("author", task.author)
                        put("title", task.title)
                        put("status", task.status.key)
                        put("extra", task.extra)
                    })

                    it.resumeWith(Result.success(result))
                }
            }
        }
    }

    private fun updateTask(id: Long, task: Task) {
        queueExecutor.execute {
            taskDatabase.execSQL("UPDATE tasks SET status = ?, extra = ? WHERE id = ?",
                arrayOf(
                    task.status.key,
                    task.extra,
                    id.toString()
                )
            )
        }
    }

    fun clearAllTasks() {
        runBlocking {
            launch(queueExecutor.asCoroutineDispatcher()) {
                taskDatabase.execSQL("DELETE FROM tasks")
            }
        }
    }

    fun removeTask(task: Task) {
        runBlocking {
            activeTasks.entries.find { it.value.task == task }?.let {
                activeTasks.remove(it.key)
                runCatching {
                    it.value.cancel()
                }.onFailure {
                    remoteSideContext.log.warn("Failed to cancel task ${task.hash}")
                }
            }
            launch(queueExecutor.asCoroutineDispatcher()) {
                taskDatabase.execSQL("DELETE FROM tasks WHERE hash = ?", arrayOf(task.hash))
            }
        }
    }

    fun createPendingTask(task: Task): PendingTask {
        val taskId = putNewTask(task)
        task.changeListener = {
            updateTask(taskId, task)
        }

        val pendingTask = PendingTask(taskId, task)
        activeTasks[taskId] = pendingTask
        return pendingTask
    }

    fun getTaskByHash(hash: String?): Task? {
        if (hash == null) return null
        taskDatabase.rawQuery("SELECT * FROM tasks WHERE hash = ?", arrayOf(hash)).use { cursor ->
            if (cursor.moveToNext()) {
                return readTaskFromCursor(cursor)
            }
        }
        return null
    }

    fun getActiveTasks() = activeTasks

    fun fetchStoredTasks(lastId: Long = Long.MAX_VALUE, limit: Int = 10): Map<Long, Task> {
        val tasks = mutableMapOf<Long, Task>()
        val invalidTasks = mutableListOf<Long>()

        taskDatabase.rawQuery("SELECT * FROM tasks WHERE id < ? ORDER BY id DESC LIMIT ?", arrayOf(lastId.toString(), limit.toString())).use { cursor ->
            while (cursor.moveToNext()) {
                runCatching {
                    val task = readTaskFromCursor(cursor)
                    if (!task.status.isFinalStage()) { task.status = TaskStatus.FAILURE }
                    tasks[cursor.getLong("id")] = task
                }.onFailure {
                    invalidTasks.add(cursor.getLong("id"))
                    remoteSideContext.log.warn("Failed to read task ${cursor.getLong("id")}")
                }
            }
        }

        invalidTasks.forEach {
            queueExecutor.execute {
                taskDatabase.execSQL("DELETE FROM tasks WHERE id = ?", arrayOf(it.toString()))
            }
        }

        return tasks
    }
}
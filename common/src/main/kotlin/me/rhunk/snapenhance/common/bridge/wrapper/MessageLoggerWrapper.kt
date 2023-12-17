package me.rhunk.snapenhance.common.bridge.wrapper

import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import kotlinx.coroutines.*
import me.rhunk.snapenhance.bridge.MessageLoggerInterface
import me.rhunk.snapenhance.common.data.StoryData
import me.rhunk.snapenhance.common.util.SQLiteDatabaseHelper
import me.rhunk.snapenhance.common.util.ktx.getBlobOrNull
import me.rhunk.snapenhance.common.util.ktx.getLongOrNull
import me.rhunk.snapenhance.common.util.ktx.getStringOrNull
import java.io.File
import java.util.UUID

class MessageLoggerWrapper(
    val databaseFile: File
): MessageLoggerInterface.Stub() {
    private var _database: SQLiteDatabase? = null
    @OptIn(ExperimentalCoroutinesApi::class)
    private val coroutineScope = CoroutineScope(Dispatchers.IO.limitedParallelism(1))

    private val database get() = synchronized(this) {
        _database?.takeIf { it.isOpen } ?: run {
            _database?.close()
            val openedDatabase = SQLiteDatabase.openDatabase(databaseFile.absolutePath, null, SQLiteDatabase.CREATE_IF_NECESSARY or SQLiteDatabase.OPEN_READWRITE)
            SQLiteDatabaseHelper.createTablesFromSchema(openedDatabase, mapOf(
                "messages" to listOf(
                    "id INTEGER PRIMARY KEY",
                    "added_timestamp BIGINT",
                    "conversation_id VARCHAR",
                    "message_id BIGINT",
                    "message_data BLOB"
                ),
                "stories" to listOf(
                    "id INTEGER PRIMARY KEY",
                    "added_timestamp BIGINT",
                    "user_id VARCHAR",
                    "posted_timestamp BIGINT",
                    "created_timestamp BIGINT",
                    "url VARCHAR",
                    "encryption_key BLOB",
                    "encryption_iv BLOB"
                )
            ))
            _database = openedDatabase
            openedDatabase
        }
    }

    protected fun finalize() {
        _database?.close()
    }

    fun init() {

    }

    override fun getLoggedIds(conversationId: Array<String>, limit: Int): LongArray {
        if (conversationId.any {
            runCatching { UUID.fromString(it) }.isFailure
        }) return longArrayOf()

        val cursor = database.rawQuery("SELECT message_id FROM messages WHERE conversation_id IN (${
            conversationId.joinToString(
                ","
            ) { "'$it'" }
        }) ORDER BY message_id DESC LIMIT $limit", null)

        val ids = mutableListOf<Long>()
        while (cursor.moveToNext()) {
            ids.add(cursor.getLong(0))
        }
        cursor.close()
        return ids.toLongArray()
    }

    override fun getMessage(conversationId: String?, id: Long): ByteArray? {
        val cursor = database.rawQuery("SELECT message_data FROM messages WHERE conversation_id = ? AND message_id = ?", arrayOf(conversationId, id.toString()))
        val message: ByteArray? = if (cursor.moveToFirst()) {
            cursor.getBlob(0)
        } else {
            null
        }
        cursor.close()
        return message
    }

    override fun addMessage(conversationId: String, messageId: Long, serializedMessage: ByteArray) {
        val cursor = database.rawQuery("SELECT message_id FROM messages WHERE conversation_id = ? AND message_id = ?", arrayOf(conversationId, messageId.toString()))
        val state = cursor.moveToFirst()
        cursor.close()
        if (state) return
        runBlocking {
            withContext(coroutineScope.coroutineContext) {
                database.insert("messages", null, ContentValues().apply {
                    put("added_timestamp", System.currentTimeMillis())
                    put("conversation_id", conversationId)
                    put("message_id", messageId)
                    put("message_data", serializedMessage)
                })
            }
        }
    }

    fun purgeAll(maxAge: Long? = null) {
        coroutineScope.launch {
            maxAge?.let {
                val maxTime = System.currentTimeMillis() - it
                database.execSQL("DELETE FROM messages WHERE added_timestamp < ?", arrayOf(maxTime.toString()))
                database.execSQL("DELETE FROM stories WHERE added_timestamp < ?", arrayOf(maxTime.toString()))
            } ?: run {
                database.execSQL("DELETE FROM messages")
                database.execSQL("DELETE FROM stories")
            }
        }
    }

    fun getStoredMessageCount(): Int {
        val cursor = database.rawQuery("SELECT COUNT(*) FROM messages", null)
        cursor.moveToFirst()
        val count = cursor.getInt(0)
        cursor.close()
        return count
    }

    fun getStoredStoriesCount(): Int {
        val cursor = database.rawQuery("SELECT COUNT(*) FROM stories", null)
        cursor.moveToFirst()
        val count = cursor.getInt(0)
        cursor.close()
        return count
    }

    override fun deleteMessage(conversationId: String, messageId: Long) {
        coroutineScope.launch {
            database.execSQL("DELETE FROM messages WHERE conversation_id = ? AND message_id = ?", arrayOf(conversationId, messageId.toString()))
        }
    }

    override fun addStory(userId: String, url: String, postedAt: Long, createdAt: Long, key: ByteArray?, iv: ByteArray?): Boolean {
        if (database.rawQuery("SELECT id FROM stories WHERE user_id = ? AND url = ?", arrayOf(userId, url)).use {
            it.moveToFirst()
        }) {
            return false
        }
        runBlocking {
            withContext(coroutineScope.coroutineContext) {
                database.insert("stories", null, ContentValues().apply {
                    put("user_id", userId)
                    put("added_timestamp", System.currentTimeMillis())
                    put("url", url)
                    put("posted_timestamp", postedAt)
                    put("created_timestamp", createdAt)
                    put("encryption_key", key)
                    put("encryption_iv", iv)
                })
            }
        }
        return true
    }

    fun getStories(userId: String, from: Long, limit: Int = Int.MAX_VALUE): Map<Long, StoryData> {
        val stories = sortedMapOf<Long, StoryData>()
        database.rawQuery("SELECT * FROM stories WHERE user_id = ? AND posted_timestamp < ? ORDER BY posted_timestamp DESC LIMIT $limit", arrayOf(userId, from.toString())).use {
            while (it.moveToNext()) {
                stories[it.getLongOrNull("posted_timestamp") ?: continue] = StoryData(
                    url = it.getStringOrNull("url") ?: continue,
                    postedAt = it.getLongOrNull("posted_timestamp") ?: continue,
                    createdAt = it.getLongOrNull("created_timestamp") ?: continue,
                    key = it.getBlobOrNull("encryption_key"),
                    iv = it.getBlobOrNull("encryption_iv")
                )
            }
        }
        return stories
    }
}
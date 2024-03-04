package me.rhunk.snapenhance.common.bridge.wrapper

import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import kotlinx.coroutines.*
import me.rhunk.snapenhance.bridge.logger.LoggerInterface
import me.rhunk.snapenhance.common.data.StoryData
import me.rhunk.snapenhance.common.util.SQLiteDatabaseHelper
import me.rhunk.snapenhance.common.util.ktx.getBlobOrNull
import me.rhunk.snapenhance.common.util.ktx.getIntOrNull
import me.rhunk.snapenhance.common.util.ktx.getLongOrNull
import me.rhunk.snapenhance.common.util.ktx.getStringOrNull
import java.io.File
import java.util.UUID


class LoggedMessage(
    val messageId: Long,
    val timestamp: Long,
    val messageData: ByteArray
)

class TrackerLog(
    val id: Int,
    val timestamp: Long,
    val conversationId: String,
    val conversationTitle: String?,
    val isGroup: Boolean,
    val username: String,
    val userId: String,
    val eventType: String,
    val data: String
)

class LoggerWrapper(
    val databaseFile: File
): LoggerInterface.Stub() {
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
                ),
                "tracker_events" to listOf(
                    "id INTEGER PRIMARY KEY",
                    "timestamp BIGINT",
                    "conversation_id CHAR(36)",
                    "conversation_title VARCHAR",
                    "is_group BOOLEAN",
                    "username VARCHAR",
                    "user_id VARCHAR",
                    "event_type VARCHAR",
                    "data VARCHAR"
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

        return database.rawQuery("SELECT message_id FROM messages WHERE conversation_id IN (${
            conversationId.joinToString(",") { "'$it'" }
        }) ORDER BY message_id DESC LIMIT $limit", null).use {
            val ids = mutableListOf<Long>()
            while (it.moveToNext()) {
                ids.add(it.getLong(0))
            }
            ids.toLongArray()
        }
    }

    override fun getMessage(conversationId: String?, id: Long): ByteArray? {
        return database.rawQuery(
            "SELECT message_data FROM messages WHERE conversation_id = ? AND message_id = ?",
            arrayOf(conversationId, id.toString())
        ).use {
            if (it.moveToFirst()) it.getBlob(0) else null
        }
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
        return database.rawQuery("SELECT COUNT(*) FROM messages", null).use {
            it.moveToFirst()
            it.getInt(0)
        }
    }

    fun getStoredStoriesCount(): Int {
        return database.rawQuery("SELECT COUNT(*) FROM stories", null).use {
            it.moveToFirst()
            it.getInt(0)
        }
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

    override fun logTrackerEvent(
        conversationId: String,
        conversationTitle: String?,
        isGroup: Boolean,
        username: String,
        userId: String,
        eventType: String,
        data: String
    ) {
        runBlocking {
            withContext(coroutineScope.coroutineContext) {
                database.insert("tracker_events", null, ContentValues().apply {
                    put("timestamp", System.currentTimeMillis())
                    put("conversation_id", conversationId)
                    put("conversation_title", conversationTitle)
                    put("is_group", isGroup)
                    put("username", username)
                    put("user_id", userId)
                    put("event_type", eventType)
                    put("data", data)
                })
            }
        }
    }

    fun deleteTrackerLog(id: Int) {
        coroutineScope.launch {
            database.execSQL("DELETE FROM tracker_events WHERE id = ?", arrayOf(id.toString()))
        }
    }

    fun getLogs(
        lastTimestamp: Long,
        filter: ((TrackerLog) -> Boolean)? = null
    ): List<TrackerLog> {
        return database.rawQuery("SELECT * FROM tracker_events WHERE timestamp < ? ORDER BY timestamp DESC", arrayOf(lastTimestamp.toString())).use {
            val logs = mutableListOf<TrackerLog>()
            while (it.moveToNext() && logs.size < 50) {
                val log = TrackerLog(
                    id = it.getIntOrNull("id") ?: continue,
                    timestamp = it.getLongOrNull("timestamp") ?: continue,
                    conversationId = it.getStringOrNull("conversation_id") ?: continue,
                    conversationTitle = it.getStringOrNull("conversation_title"),
                    isGroup = it.getIntOrNull("is_group") == 1,
                    username = it.getStringOrNull("username") ?: continue,
                    userId = it.getStringOrNull("user_id") ?: continue,
                    eventType = it.getStringOrNull("event_type") ?: continue,
                    data = it.getStringOrNull("data") ?: continue
                )
                if (filter != null && !filter(log)) continue
                logs.add(log)
            }
            logs
        }
    }

    fun findConversation(search: String): List<String> {
        return database.rawQuery("SELECT DISTINCT conversation_id FROM tracker_events WHERE is_group = 1 AND conversation_id LIKE ?", arrayOf("%$search%")).use {
            val conversations = mutableListOf<String>()
            while (it.moveToNext()) {
                conversations.add(it.getString(0))
            }
            conversations
        }
    }

    fun findUsername(search: String): List<String> {
        return database.rawQuery("SELECT DISTINCT username FROM tracker_events WHERE username LIKE ?", arrayOf("%$search%")).use {
            val usernames = mutableListOf<String>()
            while (it.moveToNext()) {
                usernames.add(it.getString(0))
            }
            usernames
        }
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

    fun getAllConversations(): List<String> {
        return database.rawQuery("SELECT DISTINCT conversation_id FROM messages", null).use {
            val conversations = mutableListOf<String>()
            while (it.moveToNext()) {
                conversations.add(it.getString(0))
            }
            conversations
        }
    }

    fun fetchMessages(
        conversationId: String,
        fromTimestamp: Long,
        limit: Int,
        reverseOrder: Boolean = true,
        filter: ((LoggedMessage) -> Boolean)? = null
    ): List<LoggedMessage> {
        val messages = mutableListOf<LoggedMessage>()
        database.rawQuery(
            "SELECT * FROM messages WHERE conversation_id = ? AND added_timestamp ${if (reverseOrder) "<" else ">"} ? ORDER BY added_timestamp ${if (reverseOrder) "DESC" else "ASC"}",
            arrayOf(conversationId, fromTimestamp.toString())
        ).use {
            while (it.moveToNext() && messages.size < limit) {
                val message = LoggedMessage(
                    messageId = it.getLongOrNull("message_id") ?: continue,
                    timestamp = it.getLongOrNull("added_timestamp") ?: continue,
                    messageData = it.getBlobOrNull("message_data") ?: continue
                )
                if (filter != null && !filter(message)) continue
                messages.add(message)
            }
        }
        return messages
    }
}
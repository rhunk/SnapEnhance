package me.rhunk.snapenhance.common.bridge.wrapper

import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import kotlinx.coroutines.*
import me.rhunk.snapenhance.bridge.MessageLoggerInterface
import me.rhunk.snapenhance.common.util.SQLiteDatabaseHelper
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
                    "conversation_id VARCHAR",
                    "message_id BIGINT",
                    "message_data BLOB"
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

    override fun addMessage(conversationId: String, messageId: Long, serializedMessage: ByteArray): Boolean {
        val cursor = database.rawQuery("SELECT message_id FROM messages WHERE conversation_id = ? AND message_id = ?", arrayOf(conversationId, messageId.toString()))
        val state = cursor.moveToFirst()
        cursor.close()
        if (state) {
            return false
        }
        runBlocking {
            withContext(coroutineScope.coroutineContext) {
                database.insert("messages", null, ContentValues().apply {
                    put("conversation_id", conversationId)
                    put("message_id", messageId)
                    put("message_data", serializedMessage)
                })
            }
        }
        return true
    }

    fun clearMessages() {
        coroutineScope.launch {
            database.execSQL("DELETE FROM messages")
        }
    }

    fun getStoredMessageCount(): Int {
        val cursor = database.rawQuery("SELECT COUNT(*) FROM messages", null)
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
}
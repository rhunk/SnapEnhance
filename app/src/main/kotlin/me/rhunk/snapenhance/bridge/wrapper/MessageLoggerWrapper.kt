package me.rhunk.snapenhance.bridge.wrapper

import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import me.rhunk.snapenhance.util.SQLiteDatabaseHelper
import java.io.File

class MessageLoggerWrapper(
    private val databaseFile: File
) {

    lateinit var database: SQLiteDatabase

    fun init() {
        database = SQLiteDatabase.openDatabase(databaseFile.absolutePath, null, SQLiteDatabase.CREATE_IF_NECESSARY or SQLiteDatabase.OPEN_READWRITE)
        SQLiteDatabaseHelper.createTablesFromSchema(database, mapOf(
            "messages" to listOf(
                "id INTEGER PRIMARY KEY",
                "conversation_id VARCHAR",
                "message_id BIGINT",
                "message_data BLOB"
            )
        ))
    }

    fun deleteMessage(conversationId: String, messageId: Long) {
        database.execSQL("DELETE FROM messages WHERE conversation_id = ? AND message_id = ?", arrayOf(conversationId, messageId.toString()))
    }

    fun addMessage(conversationId: String, messageId: Long, serializedMessage: ByteArray): Boolean {
        val cursor = database.rawQuery("SELECT message_id FROM messages WHERE conversation_id = ? AND message_id = ?", arrayOf(conversationId, messageId.toString()))
        val state = cursor.moveToFirst()
        cursor.close()
        if (state) {
            return false
        }
        database.insert("messages", null, ContentValues().apply {
            put("conversation_id", conversationId)
            put("message_id", messageId)
            put("message_data", serializedMessage)
        })
        return true
    }

    fun getMessage(conversationId: String, messageId: Long): Pair<Boolean, ByteArray?> {
        val cursor = database.rawQuery("SELECT message_data FROM messages WHERE conversation_id = ? AND message_id = ?", arrayOf(conversationId, messageId.toString()))
        val state = cursor.moveToFirst()
        val message: ByteArray? = if (state) {
            cursor.getBlob(0)
        } else {
            null
        }
        cursor.close()
        return Pair(state, message)
    }

    fun getMessageIds(conversationId: String, limit: Int): List<Long> {
        val cursor = database.rawQuery("SELECT message_id FROM messages WHERE conversation_id = ? ORDER BY message_id DESC LIMIT ?", arrayOf(conversationId, limit.toString()))
        val messageIds = mutableListOf<Long>()
        while (cursor.moveToNext()) {
            messageIds.add(cursor.getLong(0))
        }
        cursor.close()
        return messageIds
    }

    fun clearMessages() {
        database.execSQL("DELETE FROM messages")
    }
}
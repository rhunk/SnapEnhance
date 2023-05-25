package me.rhunk.snapenhance.bridge

import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import java.io.File

class MessageLoggerWrapper(
    private val databaseFile: File
) {

    lateinit var database: SQLiteDatabase

    fun init() {
        database = SQLiteDatabase.openDatabase(databaseFile.absolutePath, null, SQLiteDatabase.CREATE_IF_NECESSARY or SQLiteDatabase.OPEN_READWRITE)
        database.execSQL("CREATE TABLE IF NOT EXISTS messages (message_id INTEGER PRIMARY KEY, serialized_message BLOB)")
    }

    fun addMessage(messageId: Long, serializedMessage: ByteArray): Boolean {
        val cursor = database.rawQuery("SELECT message_id FROM messages WHERE message_id = ?", arrayOf(messageId.toString()))
        val state = cursor.moveToFirst()
        cursor.close()
        if (state) {
            return false
        }
        database.insert("messages", null, ContentValues().apply {
            put("message_id", messageId)
            put("serialized_message", serializedMessage)
        })
        return true
    }

    fun clearMessages() {
        database.execSQL("DELETE FROM messages")
    }

    fun getMessage(messageId: Long): Pair<Boolean, ByteArray?> {
        val cursor = database.rawQuery("SELECT serialized_message FROM messages WHERE message_id = ?", arrayOf(messageId.toString()))
        val state = cursor.moveToFirst()
        val message: ByteArray? = if (state) {
            cursor.getBlob(0)
        } else {
            null
        }
        cursor.close()
        return Pair(state, message)
    }
}
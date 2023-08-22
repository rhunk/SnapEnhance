package me.rhunk.snapenhance.database

import android.annotation.SuppressLint
import android.database.sqlite.SQLiteDatabase
import me.rhunk.snapenhance.Logger
import me.rhunk.snapenhance.ModContext
import me.rhunk.snapenhance.database.objects.*
import me.rhunk.snapenhance.manager.Manager
import java.io.File

@SuppressLint("Range")
class DatabaseAccess(private val context: ModContext) : Manager {
    private val databaseLock = Any()

    private val arroyoDatabase: File by lazy {
        context.androidContext.getDatabasePath("arroyo.db")
    }

    private val mainDatabase: File by lazy {
        context.androidContext.getDatabasePath("main.db")
    }

    private fun openMain(): SQLiteDatabase {
        return SQLiteDatabase.openDatabase(
            mainDatabase.absolutePath,
            null,
            SQLiteDatabase.OPEN_READONLY
        )!!
    }

    private fun openArroyo(): SQLiteDatabase {
        return SQLiteDatabase.openDatabase(
            arroyoDatabase.absolutePath,
            null,
            SQLiteDatabase.OPEN_READONLY
        )!!
    }

    fun hasArroyo(): Boolean {
        return arroyoDatabase.exists()
    }

    private fun <T> safeDatabaseOperation(
        database: SQLiteDatabase,
        query: (SQLiteDatabase) -> T?
    ): T? {
        synchronized(databaseLock) {
            return runCatching {
                query(database)
            }.onFailure {
                Logger.xposedLog("Database operation failed", it)
            }.getOrNull()
        }
    }

    private fun <T : DatabaseObject> readDatabaseObject(
        obj: T,
        database: SQLiteDatabase,
        table: String,
        where: String,
        args: Array<String>
    ): T? {
        val cursor = database.rawQuery("SELECT * FROM $table WHERE $where", args)
        if (!cursor.moveToFirst()) {
            cursor.close()
            return null
        }
        try {
            obj.write(cursor)
        } catch (e: Throwable) {
            Logger.xposedLog(e)
        }
        cursor.close()
        return obj
    }

    fun getFeedEntryByUserId(userId: String): FriendFeedEntry? {
        return safeDatabaseOperation(openMain()) { database ->
            readDatabaseObject(
                FriendFeedEntry(),
                database,
                "FriendsFeedView",
                "friendUserId = ?",
                arrayOf(userId)
            )
        }
    }

    val myUserId by lazy {
        safeDatabaseOperation(openArroyo()) { arroyoDatabase: SQLiteDatabase ->
            val cursor = arroyoDatabase.rawQuery(buildString {
                append("SELECT * FROM required_values WHERE key = 'USERID'")
            }, null)

            if (!cursor.moveToFirst()) {
                cursor.close()
                return@safeDatabaseOperation null
            }

            val userId = cursor.getString(cursor.getColumnIndex("value"))
            cursor.close()
            userId
        }!!
    }

    fun getFeedEntryByConversationId(conversationId: String): FriendFeedEntry? {
        return safeDatabaseOperation(openMain()) {
            readDatabaseObject(
                FriendFeedEntry(),
                it,
                "FriendsFeedView",
                "key = ?",
                arrayOf(conversationId)
            )
        }
    }

    fun getFriendInfo(userId: String): FriendInfo? {
        return safeDatabaseOperation(openMain()) {
            readDatabaseObject(
                FriendInfo(),
                it,
                "FriendWithUsername",
                "userId = ?",
                arrayOf(userId)
            )
        }
    }

    fun getFeedEntries(limit: Int): List<FriendFeedEntry> {
        return safeDatabaseOperation(openMain()) { database ->
            val cursor = database.rawQuery(
                "SELECT * FROM FriendsFeedView ORDER BY _id LIMIT ?",
                arrayOf(limit.toString())
            )
            val list = mutableListOf<FriendFeedEntry>()
            while (cursor.moveToNext()) {
                val friendFeedEntry = FriendFeedEntry()
                try {
                    friendFeedEntry.write(cursor)
                } catch (_: Throwable) {}
                list.add(friendFeedEntry)
            }
            cursor.close()
            list
        } ?: emptyList()
    }

    fun getConversationMessageFromId(clientMessageId: Long): ConversationMessage? {
        return safeDatabaseOperation(openArroyo()) {
            readDatabaseObject(
                ConversationMessage(),
                it,
                "conversation_message",
                "client_message_id = ?",
                arrayOf(clientMessageId.toString())
            )
        }
    }

    fun getConversationType(conversationId: String): Int? {
        return safeDatabaseOperation(openArroyo()) {
            val cursor = it.rawQuery(
                "SELECT * FROM user_conversation WHERE client_conversation_id = ?",
                arrayOf(conversationId)
            )
            if (!cursor.moveToFirst()) {
                cursor.close()
                return@safeDatabaseOperation null
            }
            val type = cursor.getInt(cursor.getColumnIndex("conversation_type"))
            cursor.close()
            type
        }
    }

    fun getConversationLinkFromUserId(userId: String): UserConversationLink? {
        return safeDatabaseOperation(openArroyo()) {
            readDatabaseObject(
                UserConversationLink(),
                it,
                "user_conversation",
                "user_id = ? AND conversation_type = 0",
                arrayOf(userId)
            )
        }
    }

    fun getDMOtherParticipant(conversationId: String): String? {
        return safeDatabaseOperation(openArroyo()) { cursor ->
            val query = cursor.rawQuery(
                "SELECT * FROM user_conversation WHERE client_conversation_id = ? AND conversation_type = 0",
                arrayOf(conversationId)
            )
            val participants = mutableListOf<String>()
            while (query.moveToNext()) {
                participants.add(query.getString(query.getColumnIndex("user_id")))
            }
            query.close()
            participants.firstOrNull { it != myUserId }
        }
    }


    fun getStoryEntryFromId(storyId: String): StoryEntry? {
        return safeDatabaseOperation(openMain()) {
            readDatabaseObject(StoryEntry(), it, "Story", "storyId = ?", arrayOf(storyId))
        }
    }

    fun getConversationParticipants(conversationId: String): List<String>? {
        return safeDatabaseOperation(openArroyo()) { arroyoDatabase: SQLiteDatabase ->
            val cursor = arroyoDatabase.rawQuery(
                "SELECT * FROM user_conversation WHERE client_conversation_id = ?",
                arrayOf(conversationId)
            )
            if (!cursor.moveToFirst()) {
                cursor.close()
                return@safeDatabaseOperation emptyList()
            }
            val participants = mutableListOf<String>()
            do {
                participants.add(cursor.getString(cursor.getColumnIndex("user_id")))
            } while (cursor.moveToNext())
            cursor.close()
            participants
        }
    }

    fun getMessagesFromConversationId(
        conversationId: String,
        limit: Int
    ): List<ConversationMessage>? {
        return safeDatabaseOperation(openArroyo()) { arroyoDatabase: SQLiteDatabase ->
            val cursor = arroyoDatabase.rawQuery(
                "SELECT * FROM conversation_message WHERE client_conversation_id = ? ORDER BY creation_timestamp DESC LIMIT ?",
                arrayOf(conversationId, limit.toString())
            )
            if (!cursor.moveToFirst()) {
                cursor.close()
                return@safeDatabaseOperation emptyList()
            }
            val messages = mutableListOf<ConversationMessage>()
            do {
                val message = ConversationMessage()
                message.write(cursor)
                messages.add(message)
            } while (cursor.moveToNext())
            cursor.close()
            messages
        }
    }
}
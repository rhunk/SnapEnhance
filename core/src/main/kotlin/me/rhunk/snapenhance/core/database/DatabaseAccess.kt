package me.rhunk.snapenhance.core.database

import android.annotation.SuppressLint
import android.database.sqlite.SQLiteDatabase
import me.rhunk.snapenhance.ModContext
import me.rhunk.snapenhance.core.Logger
import me.rhunk.snapenhance.core.database.objects.ConversationMessage
import me.rhunk.snapenhance.core.database.objects.FriendFeedEntry
import me.rhunk.snapenhance.core.database.objects.FriendInfo
import me.rhunk.snapenhance.core.database.objects.StoryEntry
import me.rhunk.snapenhance.core.database.objects.UserConversationLink
import me.rhunk.snapenhance.core.util.ktx.getStringOrNull
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
            if (!database.isOpen) {
                return null
            }
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
    ): T? = database.rawQuery("SELECT * FROM $table WHERE $where", args).use {
        if (!it.moveToFirst()) {
            return null
        }
        try {
            obj.write(it)
        } catch (e: Throwable) {
            context.log.error("Failed to read database object", e)
        }
        obj
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
            arroyoDatabase.rawQuery(buildString {
                append("SELECT * FROM required_values WHERE key = 'USERID'")
            }, null).use { query ->
                if (!query.moveToFirst()) {
                    return@safeDatabaseOperation null
                }
                query.getStringOrNull("value")!!
            }
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
            database.rawQuery(
                "SELECT * FROM FriendsFeedView ORDER BY _id LIMIT ?",
                arrayOf(limit.toString())
            ).use { query ->
                val list = mutableListOf<FriendFeedEntry>()
                while (query.moveToNext()) {
                    val friendFeedEntry = FriendFeedEntry()
                    try {
                        friendFeedEntry.write(query)
                    } catch (_: Throwable) {}
                    list.add(friendFeedEntry)
                }
                list
            }
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
        return safeDatabaseOperation(openArroyo()) { database ->
            database.rawQuery(
                "SELECT * FROM user_conversation WHERE client_conversation_id = ?",
                arrayOf(conversationId)
            ).use { query ->
                if (!query.moveToFirst()) {
                    return@safeDatabaseOperation null
                }
                query.getInt(query.getColumnIndex("conversation_type"))
            }
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
            cursor.rawQuery(
                "SELECT * FROM user_conversation WHERE client_conversation_id = ? AND conversation_type = 0",
                arrayOf(conversationId)
            ).use { query ->
                val participants = mutableListOf<String>()
                if (!query.moveToFirst()) {
                    return@safeDatabaseOperation null
                }
                do {
                    participants.add(query.getString(query.getColumnIndex("user_id")))
                } while (query.moveToNext())
                participants.firstOrNull { it != myUserId }
            }
        }
    }


    fun getStoryEntryFromId(storyId: String): StoryEntry? {
        return safeDatabaseOperation(openMain()) {
            readDatabaseObject(StoryEntry(), it, "Story", "storyId = ?", arrayOf(storyId))
        }
    }

    fun getConversationParticipants(conversationId: String): List<String>? {
        return safeDatabaseOperation(openArroyo()) { arroyoDatabase: SQLiteDatabase ->
            arroyoDatabase.rawQuery(
                "SELECT * FROM user_conversation WHERE client_conversation_id = ?",
                arrayOf(conversationId)
            ).use {
                if (!it.moveToFirst()) {
                    return@safeDatabaseOperation null
                }
                val participants = mutableListOf<String>()
                do {
                    participants.add(it.getString(it.getColumnIndex("user_id")))
                } while (it.moveToNext())
                participants
            }
        }
    }

    fun getMessagesFromConversationId(
        conversationId: String,
        limit: Int
    ): List<ConversationMessage>? {
        return safeDatabaseOperation(openArroyo()) { arroyoDatabase: SQLiteDatabase ->
            arroyoDatabase.rawQuery(
                "SELECT * FROM conversation_message WHERE client_conversation_id = ? ORDER BY creation_timestamp DESC LIMIT ?",
                arrayOf(conversationId, limit.toString())
            ).use { query ->
                if (!query.moveToFirst()) {
                    return@safeDatabaseOperation null
                }
                val messages = mutableListOf<ConversationMessage>()
                do {
                    val message = ConversationMessage()
                    message.write(query)
                    messages.add(message)
                } while (query.moveToNext())
                messages
            }
        }
    }

    fun getAddSource(userId: String): String? {
        return safeDatabaseOperation(openMain()) { database ->
            database.rawQuery(
                "SELECT addSource FROM FriendWhoAddedMe WHERE userId = ?",
                arrayOf(userId)
            ).use {
                if (!it.moveToFirst()) {
                    return@safeDatabaseOperation null
                }
                it.getStringOrNull("addSource")
            }
        }
    }
}
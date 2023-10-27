package me.rhunk.snapenhance.core.database

import android.annotation.SuppressLint
import android.database.sqlite.SQLiteDatabase
import me.rhunk.snapenhance.common.database.DatabaseObject
import me.rhunk.snapenhance.common.database.impl.ConversationMessage
import me.rhunk.snapenhance.common.database.impl.FriendFeedEntry
import me.rhunk.snapenhance.common.database.impl.FriendInfo
import me.rhunk.snapenhance.common.database.impl.StoryEntry
import me.rhunk.snapenhance.common.database.impl.UserConversationLink
import me.rhunk.snapenhance.common.util.ktx.getStringOrNull
import me.rhunk.snapenhance.core.ModContext
import me.rhunk.snapenhance.core.logger.CoreLogger
import me.rhunk.snapenhance.core.manager.Manager
import java.lang.ref.WeakReference

inline fun <T> SQLiteDatabase.performOperation(crossinline query: SQLiteDatabase.() -> T?): T? {
    synchronized(this) {
        if (!isOpen) {
            return null
        }
        return runCatching {
            query()
        }.onFailure {
            CoreLogger.xposedLog("Database operation failed", it)
        }.getOrNull()
    }
}

@SuppressLint("Range")
class DatabaseAccess(
    private val context: ModContext
) : Manager {
    private val dmOtherParticipantCache by lazy {
        (openArroyo().performOperation {
            rawQuery(
                "SELECT client_conversation_id, user_id FROM user_conversation WHERE conversation_type = 0 AND user_id != ?",
                arrayOf(myUserId)
            ).use { query ->
                val participants = mutableMapOf<String, String?>()
                if (!query.moveToFirst()) {
                    return@performOperation null
                }
                do {
                    participants[query.getString(query.getColumnIndex("client_conversation_id"))] =
                        query.getString(query.getColumnIndex("user_id"))
                } while (query.moveToNext())
                participants
            }
        } ?: emptyMap()).toMutableMap()
    }

    private var databaseWeakMap = mutableMapOf<String, WeakReference<SQLiteDatabase>?>()

    private fun openLocalDatabase(fileName: String): SQLiteDatabase {
        if (databaseWeakMap.containsKey(fileName)) {
            val database = databaseWeakMap[fileName]?.get()
            if (database != null && database.isOpen) return database
        }

        return runCatching {
            SQLiteDatabase.openDatabase(
                context.androidContext.getDatabasePath(fileName).absolutePath,
                null,
                SQLiteDatabase.OPEN_READONLY
            )?.also {
                databaseWeakMap[fileName] = WeakReference(it)
            }
        }.onFailure {
            context.log.error("Failed to open database $fileName, restarting!", it)
            context.softRestartApp()
        }.getOrNull() ?: throw IllegalStateException("Failed to open database $fileName")
    }

    private fun openMain() = openLocalDatabase("main.db")
    private fun openArroyo() = openLocalDatabase("arroyo.db")

    fun hasArroyo(): Boolean = context.androidContext.getDatabasePath("arroyo.db").exists()

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
        return openMain().performOperation {
            readDatabaseObject(
                FriendFeedEntry(),
                this,
                "FriendsFeedView",
                "friendUserId = ?",
                arrayOf(userId)
            )
        }
    }

    val myUserId by lazy {
        openArroyo().performOperation {
            rawQuery(buildString {
                append("SELECT value FROM required_values WHERE key = 'USERID'")
            }, null).use { query ->
                if (!query.moveToFirst()) {
                    return@performOperation null
                }
                query.getStringOrNull("value")!!
            }
        } ?: context.androidContext.getSharedPreferences("user_session_shared_pref", 0).getString("key_user_id", null)!!
    }

    fun getFeedEntryByConversationId(conversationId: String): FriendFeedEntry? {
        return openMain().performOperation {
            readDatabaseObject(
                FriendFeedEntry(),
                this,
                "FriendsFeedView",
                "key = ?",
                arrayOf(conversationId)
            )
        }
    }

    fun getFriendInfo(userId: String): FriendInfo? {
        return openMain().performOperation {
            readDatabaseObject(
                FriendInfo(),
                this,
                "FriendWithUsername",
                "userId = ?",
                arrayOf(userId)
            )
        }
    }

    fun getFeedEntries(limit: Int): List<FriendFeedEntry> {
        return openMain().performOperation {
            rawQuery(
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
        return openArroyo().performOperation {
            readDatabaseObject(
                ConversationMessage(),
                this,
                "conversation_message",
                "client_message_id = ?",
                arrayOf(clientMessageId.toString())
            )
        }
    }

    fun getConversationType(conversationId: String): Int? {
        return openArroyo().performOperation {
            rawQuery(
                "SELECT conversation_type FROM user_conversation WHERE client_conversation_id = ?",
                arrayOf(conversationId)
            ).use { query ->
                if (!query.moveToFirst()) {
                    return@performOperation null
                }
                query.getInt(query.getColumnIndex("conversation_type"))
            }
        }
    }

    fun getConversationLinkFromUserId(userId: String): UserConversationLink? {
        return openArroyo().performOperation {
            readDatabaseObject(
                UserConversationLink(),
                this,
                "user_conversation",
                "user_id = ? AND conversation_type = 0",
                arrayOf(userId)
            )
        }
    }

    fun getDMOtherParticipant(conversationId: String): String? {
        if (dmOtherParticipantCache.containsKey(conversationId)) return dmOtherParticipantCache[conversationId]
        return openArroyo().performOperation {
            rawQuery(
                "SELECT user_id FROM user_conversation WHERE client_conversation_id = ? AND conversation_type = 0",
                arrayOf(conversationId)
            ).use { query ->
                val participants = mutableListOf<String>()
                if (!query.moveToFirst()) {
                    return@performOperation null
                }
                do {
                    participants.add(query.getString(query.getColumnIndex("user_id")))
                } while (query.moveToNext())
                participants.firstOrNull { it != myUserId }
            }
        }.also { dmOtherParticipantCache[conversationId] = it }
    }


    fun getStoryEntryFromId(storyId: String): StoryEntry? {
        return openMain().performOperation  {
            readDatabaseObject(StoryEntry(), this, "Story", "storyId = ?", arrayOf(storyId))
        }
    }

    fun getConversationParticipants(conversationId: String): List<String>? {
        return openArroyo().performOperation {
            rawQuery(
                "SELECT user_id FROM user_conversation WHERE client_conversation_id = ?",
                arrayOf(conversationId)
            ).use {
                if (!it.moveToFirst()) {
                    return@performOperation null
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
        return openArroyo().performOperation {
            rawQuery(
                "SELECT * FROM conversation_message WHERE client_conversation_id = ? ORDER BY creation_timestamp DESC LIMIT ?",
                arrayOf(conversationId, limit.toString())
            ).use { query ->
                if (!query.moveToFirst()) {
                    return@performOperation null
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
        return openMain().performOperation  {
            rawQuery(
                "SELECT addSource FROM FriendWhoAddedMe WHERE userId = ?",
                arrayOf(userId)
            ).use {
                if (!it.moveToFirst()) {
                    return@performOperation null
                }
                it.getStringOrNull("addSource")
            }
        }
    }
}
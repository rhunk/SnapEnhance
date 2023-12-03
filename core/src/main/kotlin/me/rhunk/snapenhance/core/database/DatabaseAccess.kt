package me.rhunk.snapenhance.core.database

import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteDatabase.OpenParams
import android.database.sqlite.SQLiteDatabaseCorruptException
import me.rhunk.snapenhance.common.database.DatabaseObject
import me.rhunk.snapenhance.common.database.impl.ConversationMessage
import me.rhunk.snapenhance.common.database.impl.FriendFeedEntry
import me.rhunk.snapenhance.common.database.impl.FriendInfo
import me.rhunk.snapenhance.common.database.impl.StoryEntry
import me.rhunk.snapenhance.common.database.impl.UserConversationLink
import me.rhunk.snapenhance.common.util.ktx.getIntOrNull
import me.rhunk.snapenhance.common.util.ktx.getInteger
import me.rhunk.snapenhance.common.util.ktx.getStringOrNull
import me.rhunk.snapenhance.core.ModContext
import me.rhunk.snapenhance.core.manager.Manager


class DatabaseAccess(
    private val context: ModContext
) : Manager {
    companion object {
        val DATABASES = mapOf(
            "main" to "main.db",
            "arroyo" to "arroyo.db"
        )
    }
    private val mainDb by lazy { openLocalDatabase("main") }
    private val arroyoDb by lazy { openLocalDatabase("arroyo") }

    private inline fun <T> SQLiteDatabase.performOperation(crossinline query: SQLiteDatabase.() -> T?): T? {
        return runCatching {
            synchronized(this) {
                query()
            }
        }.onFailure {
            context.log.error("Database operation failed", it)
        }.getOrNull()
    }

    private fun SQLiteDatabase.safeRawQuery(query: String, args: Array<String>? = null): Cursor? {
        return runCatching {
            rawQuery(query, args)
        }.onFailure {
            if (it !is SQLiteDatabaseCorruptException) {
                context.log.error("Failed to execute query $query", it)
                return@onFailure
            }
            context.longToast("Database ${this.path} is corrupted! Restarting ...")
            context.androidContext.deleteDatabase(this.path)
            context.crash("Database ${this.path} is corrupted!", it)
        }.getOrNull()
    }

    private val dmOtherParticipantCache by lazy {
        (arroyoDb?.performOperation {
            safeRawQuery(
                "SELECT client_conversation_id, conversation_type, user_id FROM user_conversation WHERE user_id != ?",
                arrayOf(myUserId)
            )?.use { query ->
                val participants = mutableMapOf<String, String?>()
                if (!query.moveToFirst()) {
                    return@performOperation null
                }
                do {
                    val conversationId = query.getStringOrNull("client_conversation_id") ?: continue
                    val userId = query.getStringOrNull("user_id") ?: continue
                    participants[conversationId] = when (query.getIntOrNull("conversation_type")) {
                        0 -> userId
                        else -> null
                    }
                    participants[userId] = null
                } while (query.moveToNext())
                participants
            }
        } ?: emptyMap()).toMutableMap()
    }

    private fun openLocalDatabase(databaseName: String, writeMode: Boolean = false): SQLiteDatabase? {
        val dbPath = context.androidContext.getDatabasePath(DATABASES[databaseName]!!)
        if (!dbPath.exists()) return null
        return runCatching {
            SQLiteDatabase.openDatabase(
                dbPath,
                OpenParams.Builder()
                    .setOpenFlags(
                        if (writeMode) SQLiteDatabase.OPEN_READWRITE or SQLiteDatabase.ENABLE_WRITE_AHEAD_LOGGING
                        else SQLiteDatabase.OPEN_READONLY
                    )
                    .setErrorHandler {
                        context.androidContext.deleteDatabase(dbPath.absolutePath)
                        context.softRestartApp()
                    }.build()
            )
        }.onFailure {
            context.log.error("Failed to open database $databaseName!", it)
        }.getOrNull()
    }

    fun hasMain(): Boolean = mainDb?.isOpen == true
    fun hasArroyo(): Boolean = arroyoDb?.isOpen == true

    override fun init() {
        // perform integrity check on databases
        DATABASES.forEach { (name, fileName) ->
            openLocalDatabase(name, writeMode = true)?.apply {
                rawQuery("PRAGMA integrity_check", null).use { query ->
                    if (!query.moveToFirst() || query.getString(0).lowercase() != "ok") {
                        context.log.error("Failed to perform integrity check on $fileName")
                        context.androidContext.deleteDatabase(fileName)
                        return@apply
                    }
                    context.log.verbose("database $fileName integrity check passed")
                }
            }?.close()
        }
    }

    fun finalize() {
        mainDb?.close()
        arroyoDb?.close()
        context.log.verbose("Database closed")
    }

    private fun <T : DatabaseObject> SQLiteDatabase.readDatabaseObject(
        obj: T,
        table: String,
        where: String,
        args: Array<String>
    ): T? = this.safeRawQuery("SELECT * FROM $table WHERE $where", args)?.use {
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
        return mainDb?.performOperation {
            readDatabaseObject(
                FriendFeedEntry(),
                "FriendsFeedView",
                "friendUserId = ?",
                arrayOf(userId)
            )
        }
    }

    val myUserId by lazy {
        context.androidContext.getSharedPreferences("user_session_shared_pref", 0).getString("key_user_id", null) ?:
        arroyoDb?.performOperation {
            safeRawQuery(buildString {
                append("SELECT value FROM required_values WHERE key = 'USERID'")
            }, null)?.use { query ->
                if (!query.moveToFirst()) {
                    return@performOperation null
                }
                query.getStringOrNull("value")!!
            }
        }!!
    }

    fun getFeedEntryByConversationId(conversationId: String): FriendFeedEntry? {
        return mainDb?.performOperation {
            readDatabaseObject(
                FriendFeedEntry(),
                "FriendsFeedView",
                "key = ?",
                arrayOf(conversationId)
            )
        }
    }

    fun getFriendInfo(userId: String): FriendInfo? {
        return mainDb?.performOperation {
            readDatabaseObject(
                FriendInfo(),
                "FriendWithUsername",
                "userId = ?",
                arrayOf(userId)
            )
        }
    }

    fun getAllFriends(): List<FriendInfo> {
        return mainDb?.performOperation {
            safeRawQuery(
                "SELECT * FROM FriendWithUsername",
                null
            )?.use { query ->
                val list = mutableListOf<FriendInfo>()
                while (query.moveToNext()) {
                    val friendInfo = FriendInfo()
                    try {
                        friendInfo.write(query)
                    } catch (_: Throwable) {}
                    list.add(friendInfo)
                }
                list
            }
        } ?: emptyList()
    }

    fun getFeedEntries(limit: Int): List<FriendFeedEntry> {
        return mainDb?.performOperation {
            safeRawQuery(
                "SELECT * FROM FriendsFeedView ORDER BY _id LIMIT ?",
                arrayOf(limit.toString())
            )?.use { query ->
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
        return arroyoDb?.performOperation {
            readDatabaseObject(
                ConversationMessage(),
                "conversation_message",
                "client_message_id = ?",
                arrayOf(clientMessageId.toString())
            )
        }
    }

    fun getConversationType(conversationId: String): Int? {
        return arroyoDb?.performOperation {
            safeRawQuery(
                "SELECT conversation_type FROM user_conversation WHERE client_conversation_id = ?",
                arrayOf(conversationId)
            )?.use { query ->
                if (!query.moveToFirst()) {
                    return@performOperation null
                }
                query.getInteger("conversation_type")
            }
        }
    }

    fun getConversationLinkFromUserId(userId: String): UserConversationLink? {
        return arroyoDb?.performOperation {
            readDatabaseObject(
                UserConversationLink(),
                "user_conversation",
                "user_id = ? AND conversation_type = 0",
                arrayOf(userId)
            )
        }
    }

    fun getDMOtherParticipant(conversationId: String): String? {
        if (dmOtherParticipantCache.containsKey(conversationId)) return dmOtherParticipantCache[conversationId]
        return arroyoDb?.performOperation {
            safeRawQuery(
                "SELECT user_id FROM user_conversation WHERE client_conversation_id = ? AND conversation_type = 0",
                arrayOf(conversationId)
            )?.use { query ->
                val participants = mutableListOf<String>()
                if (!query.moveToFirst()) {
                    return@performOperation null
                }
                do {
                    participants.add(query.getStringOrNull("user_id")!!)
                } while (query.moveToNext())
                participants.firstOrNull { it != myUserId }
            }.also { dmOtherParticipantCache[conversationId] = it }
        }
    }


    fun getStoryEntryFromId(storyId: String): StoryEntry? {
        return mainDb?.performOperation  {
            readDatabaseObject(StoryEntry(), "Story", "storyId = ?", arrayOf(storyId))
        }
    }

    fun getConversationParticipants(conversationId: String): List<String>? {
        if (dmOtherParticipantCache[conversationId] != null) return dmOtherParticipantCache[conversationId]?.let { listOf(myUserId, it) }
        return arroyoDb?.performOperation {
            safeRawQuery(
                "SELECT user_id, conversation_type FROM user_conversation WHERE client_conversation_id = ?",
                arrayOf(conversationId)
            )?.use { cursor ->
                if (!cursor.moveToFirst()) {
                    return@performOperation null
                }
                val participants = mutableListOf<String>()
                var conversationType = -1
                do {
                    if (conversationType == -1) conversationType = cursor.getInteger("conversation_type")
                    participants.add(cursor.getStringOrNull("user_id")!!)
                } while (cursor.moveToNext())

                if (!dmOtherParticipantCache.containsKey(conversationId)) {
                    dmOtherParticipantCache[conversationId] = when (conversationType) {
                        0 -> participants.firstOrNull { it != myUserId }
                        else -> null
                    }
                }
                participants
            }
        }
    }

    fun getMessagesFromConversationId(
        conversationId: String,
        limit: Int
    ): List<ConversationMessage>? {
        return arroyoDb?.performOperation {
            safeRawQuery(
                "SELECT * FROM conversation_message WHERE client_conversation_id = ? ORDER BY creation_timestamp DESC LIMIT ?",
                arrayOf(conversationId, limit.toString())
            )?.use { query ->
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
        return mainDb?.performOperation  {
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

    fun markFriendStoriesAsSeen(userId: String) {
        openLocalDatabase("main", writeMode = true)?.apply {
            performOperation {
                execSQL("UPDATE StorySnap SET viewed = 1 WHERE userId = ?", arrayOf(userId))
            }
            close()
        }
    }
}
package me.rhunk.snapenhance.core.database

import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteDatabaseCorruptException
import me.rhunk.snapenhance.common.database.DatabaseObject
import me.rhunk.snapenhance.common.database.impl.ConversationMessage
import me.rhunk.snapenhance.common.database.impl.FriendFeedEntry
import me.rhunk.snapenhance.common.database.impl.FriendInfo
import me.rhunk.snapenhance.common.database.impl.StoryEntry
import me.rhunk.snapenhance.common.database.impl.UserConversationLink
import me.rhunk.snapenhance.common.util.ktx.getInteger
import me.rhunk.snapenhance.common.util.ktx.getStringOrNull
import me.rhunk.snapenhance.core.ModContext
import me.rhunk.snapenhance.core.manager.Manager
import me.rhunk.snapenhance.core.ui.ViewAppearanceHelper
import java.io.File


class DatabaseAccess(
    private val context: ModContext
) : Manager {
    private val mainDb by lazy { openLocalDatabase("main.db") }
    private val arroyoDb by lazy { openLocalDatabase("arroyo.db") }

    private inline fun <T> SQLiteDatabase.performOperation(crossinline query: SQLiteDatabase.() -> T?): T? {
        return runCatching {
            query()
        }.onFailure {
            context.log.error("Database operation failed", it)
        }.getOrNull()
    }

    private var hasShownDatabaseError = false

    private fun showDatabaseError(databasePath: String, throwable: Throwable) {
        if (hasShownDatabaseError) return
        hasShownDatabaseError = true
        context.runOnUiThread {
            if (context.mainActivity == null) return@runOnUiThread
            ViewAppearanceHelper.newAlertDialogBuilder(context.mainActivity)
                .setTitle("SnapEnhance")
                .setMessage("Failed to query $databasePath database!\n\n${throwable.localizedMessage}\n\nRestarting Snapchat may fix this issue. If the issue persists, try to clean the app data and cache.")
                .setPositiveButton("Restart Snapchat") { _, _ ->
                    File(databasePath).takeIf { it.exists() }?.delete()
                    context.softRestartApp()
                }
                .setNegativeButton("Dismiss") { dialog, _ ->
                    dialog.dismiss()
                }.show()
        }
    }

    private fun SQLiteDatabase.safeRawQuery(query: String, args: Array<String>? = null): Cursor? {
        return runCatching {
            rawQuery(query, args)
        }.onFailure {
            if (it !is SQLiteDatabaseCorruptException) {
                context.log.error("Failed to execute query $query", it)
                showDatabaseError(this.path, it)
                return@onFailure
            }
            context.log.warn("Database ${this.path} is corrupted!")
            context.androidContext.deleteDatabase(this.path)
            showDatabaseError(this.path, it)
        }.getOrNull()
    }

    private val dmOtherParticipantCache by lazy {
        (arroyoDb?.performOperation {
            safeRawQuery(
                "SELECT client_conversation_id, user_id FROM user_conversation WHERE conversation_type = 0 AND user_id != ?",
                arrayOf(myUserId)
            )?.use { query ->
                val participants = mutableMapOf<String, String?>()
                if (!query.moveToFirst()) {
                    return@performOperation null
                }
                do {
                    participants[query.getStringOrNull("client_conversation_id")!!] = query.getStringOrNull("user_id")!!
                } while (query.moveToNext())
                participants
            }
        } ?: emptyMap()).toMutableMap()
    }

    private fun openLocalDatabase(fileName: String): SQLiteDatabase? {
        val dbPath = context.androidContext.getDatabasePath(fileName)
        if (!dbPath.exists()) return null
        return runCatching {
            SQLiteDatabase.openDatabase(
                dbPath.absolutePath,
                null,
                SQLiteDatabase.OPEN_READONLY or SQLiteDatabase.NO_LOCALIZED_COLLATORS
            )
        }.onFailure {
            context.log.error("Failed to open database $fileName!", it)
            showDatabaseError(dbPath.absolutePath, it)
        }.getOrNull()
    }

    fun hasMain(): Boolean = mainDb?.isOpen == true
    fun hasArroyo(): Boolean = arroyoDb?.isOpen == true

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
        arroyoDb?.performOperation {
            safeRawQuery(buildString {
                append("SELECT value FROM required_values WHERE key = 'USERID'")
            }, null)?.use { query ->
                if (!query.moveToFirst()) {
                    return@performOperation null
                }
                query.getStringOrNull("value")!!
            }
        } ?: context.androidContext.getSharedPreferences("user_session_shared_pref", 0).getString("key_user_id", null)!!
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
            }
        }.also { dmOtherParticipantCache[conversationId] = it }
    }


    fun getStoryEntryFromId(storyId: String): StoryEntry? {
        return mainDb?.performOperation  {
            readDatabaseObject(StoryEntry(), "Story", "storyId = ?", arrayOf(storyId))
        }
    }

    fun getConversationParticipants(conversationId: String): List<String>? {
        return arroyoDb?.performOperation {
            safeRawQuery(
                "SELECT user_id FROM user_conversation WHERE client_conversation_id = ?",
                arrayOf(conversationId)
            )?.use {
                if (!it.moveToFirst()) {
                    return@performOperation null
                }
                val participants = mutableListOf<String>()
                do {
                    participants.add(it.getStringOrNull("user_id")!!)
                } while (it.moveToNext())
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
}
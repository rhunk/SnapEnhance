package me.rhunk.snapenhance.messaging

import android.database.sqlite.SQLiteDatabase
import me.rhunk.snapenhance.Logger
import me.rhunk.snapenhance.RemoteSideContext
import me.rhunk.snapenhance.core.messaging.FriendStreaks
import me.rhunk.snapenhance.core.messaging.MessagingFriendInfo
import me.rhunk.snapenhance.core.messaging.MessagingGroupInfo
import me.rhunk.snapenhance.core.messaging.MessagingRule
import me.rhunk.snapenhance.core.messaging.SocialScope
import me.rhunk.snapenhance.database.objects.FriendInfo
import me.rhunk.snapenhance.util.SQLiteDatabaseHelper
import me.rhunk.snapenhance.util.ktx.getInteger
import me.rhunk.snapenhance.util.ktx.getLongOrNull
import me.rhunk.snapenhance.util.ktx.getStringOrNull
import java.util.concurrent.Executors


class ModDatabase(
    private val context: RemoteSideContext,
) {
    private val executor = Executors.newSingleThreadExecutor()
    private lateinit var database: SQLiteDatabase

    var receiveMessagingDataCallback: (friends: List<MessagingFriendInfo>, groups: List<MessagingGroupInfo>) -> Unit = { _, _ -> }

    fun executeAsync(block: () -> Unit) {
        executor.execute {
            runCatching {
                block()
            }.onFailure {
                Logger.error("Failed to execute async block", it)
            }
        }
    }

    fun init() {
        database = context.androidContext.openOrCreateDatabase("main.db", 0, null)
        SQLiteDatabaseHelper.createTablesFromSchema(database, mapOf(
            "friends" to listOf(
                "id INTEGER PRIMARY KEY AUTOINCREMENT",
                "userId VARCHAR UNIQUE",
                "displayName VARCHAR",
                "mutableUsername VARCHAR",
                "bitmojiId VARCHAR",
                "selfieId VARCHAR"
            ),
            "groups" to listOf(
                "id INTEGER PRIMARY KEY AUTOINCREMENT",
                "conversationId VARCHAR UNIQUE",
                "name VARCHAR",
                "participantsCount INTEGER"
            ),
            "rules" to listOf(
                "id INTEGER PRIMARY KEY AUTOINCREMENT",
                "scope VARCHAR",
                "targetUuid VARCHAR",
                //"mode VARCHAR",
                "subject VARCHAR"
            ),
            "streaks" to listOf(
                "userId VARCHAR PRIMARY KEY",
                "notify BOOLEAN",
                "expirationTimestamp BIGINT",
                "length INTEGER"
            ),
            "analytics_config" to listOf(
                "userId VARCHAR PRIMARY KEY",
                "modes VARCHAR"
            ),
            "analytics" to listOf(
                "hash VARCHAR PRIMARY KEY",
                "userId VARCHAR",
                "conversationId VARCHAR",
                "timestamp BIGINT",
                "eventName VARCHAR",
                "eventData VARCHAR"
            )
        ))
    }

    fun getGroups(): List<MessagingGroupInfo> {
        return database.rawQuery("SELECT * FROM groups", null).use { cursor ->
            val groups = mutableListOf<MessagingGroupInfo>()
            while (cursor.moveToNext()) {
                groups.add(MessagingGroupInfo(
                    conversationId = cursor.getStringOrNull("conversationId")!!,
                    name = cursor.getStringOrNull("name")!!,
                    participantsCount = cursor.getInteger("participantsCount")
                ))
            }
            groups
        }
    }

    fun getFriends(descOrder: Boolean = false): List<MessagingFriendInfo> {
        return database.rawQuery("SELECT * FROM friends ORDER BY id ${if (descOrder) "DESC" else "ASC"}", null).use { cursor ->
            val friends = mutableListOf<MessagingFriendInfo>()
            while (cursor.moveToNext()) {
                runCatching {
                    friends.add(MessagingFriendInfo(
                        userId = cursor.getStringOrNull("userId")!!,
                        displayName = cursor.getStringOrNull("displayName"),
                        mutableUsername = cursor.getStringOrNull("mutableUsername")!!,
                        bitmojiId = cursor.getStringOrNull("bitmojiId"),
                        selfieId = cursor.getStringOrNull("selfieId")
                    ))
                }.onFailure {
                    Logger.error("Failed to parse friend", it)
                }
            }
            friends
        }
    }


    fun syncGroupInfo(conversationInfo: MessagingGroupInfo) {
        executeAsync {
            try {
                database.execSQL("INSERT OR REPLACE INTO groups (conversationId, name, participantsCount) VALUES (?, ?, ?)", arrayOf(
                    conversationInfo.conversationId,
                    conversationInfo.name,
                    conversationInfo.participantsCount
                ))
            } catch (e: Exception) {
                throw e
            }
        }
    }

    fun syncFriend(friend: FriendInfo) {
        executeAsync {
            try {
                database.execSQL(
                    "INSERT OR REPLACE INTO friends (userId, displayName, mutableUsername, bitmojiId, selfieId) VALUES (?, ?, ?, ?, ?)",
                    arrayOf(
                        friend.userId,
                        friend.displayName,
                        friend.usernameForSorting!!,
                        friend.bitmojiAvatarId,
                        friend.bitmojiSelfieId
                    )
                )
                //sync streaks
                if (friend.streakLength > 0) {
                    database.execSQL("INSERT OR REPLACE INTO streaks (userId, expirationTimestamp, length) VALUES (?, ?, ?)", arrayOf(
                        friend.userId,
                        friend.streakExpirationTimestamp,
                        friend.streakLength
                    ))
                } else {
                    database.execSQL("DELETE FROM streaks WHERE userId = ?", arrayOf(friend.userId))
                }
            } catch (e: Exception) {
                throw e
            }
        }
    }

    fun getRulesFromId(type: SocialScope, targetUuid: String): List<MessagingRule> {
        return database.rawQuery("SELECT * FROM rules WHERE scope = ? AND targetUuid = ?", arrayOf(type.name, targetUuid)).use { cursor ->
            val rules = mutableListOf<MessagingRule>()
            while (cursor.moveToNext()) {
                rules.add(MessagingRule(
                    id = cursor.getInteger("id"),
                    socialScope = SocialScope.valueOf(cursor.getStringOrNull("scope")!!),
                    targetUuid = cursor.getStringOrNull("targetUuid")!!,
                    subject = cursor.getStringOrNull("subject")!!
                ))
            }
            rules
        }
    }

    fun toggleRuleFor(type: SocialScope, targetUuid: String, subject: String, enabled: Boolean) {
        executeAsync {
            if (enabled) {
                database.execSQL("INSERT OR REPLACE INTO rules (scope, targetUuid, subject) VALUES (?, ?, ?)", arrayOf(
                    type.name,
                    targetUuid,
                    subject
                ))
            } else {
                database.execSQL("DELETE FROM rules WHERE scope = ? AND targetUuid = ? AND subject = ?", arrayOf(
                    type.name,
                    targetUuid,
                    subject
                ))
            }
        }
    }

    fun getFriendInfo(userId: String): MessagingFriendInfo? {
        return database.rawQuery("SELECT * FROM friends WHERE userId = ?", arrayOf(userId)).use { cursor ->
            if (!cursor.moveToFirst()) return@use null
            MessagingFriendInfo(
                userId = cursor.getStringOrNull("userId")!!,
                displayName = cursor.getStringOrNull("displayName"),
                mutableUsername = cursor.getStringOrNull("mutableUsername")!!,
                bitmojiId = cursor.getStringOrNull("bitmojiId"),
                selfieId = cursor.getStringOrNull("selfieId")
            )
        }
    }

    fun deleteFriend(userId: String) {
        executeAsync {
            database.execSQL("DELETE FROM friends WHERE userId = ?", arrayOf(userId))
        }
    }

    fun deleteGroup(conversationId: String) {
        executeAsync {
            database.execSQL("DELETE FROM groups WHERE conversationId = ?", arrayOf(conversationId))
        }
    }

    fun getGroupInfo(conversationId: String): MessagingGroupInfo? {
        return database.rawQuery("SELECT * FROM groups WHERE conversationId = ?", arrayOf(conversationId)).use { cursor ->
            if (!cursor.moveToFirst()) return@use null
            MessagingGroupInfo(
                conversationId = cursor.getStringOrNull("conversationId")!!,
                name = cursor.getStringOrNull("name")!!,
                participantsCount = cursor.getInteger("participantsCount")
            )
        }
    }

    fun getFriendStreaks(userId: String): FriendStreaks? {
        return database.rawQuery("SELECT * FROM streaks WHERE userId = ?", arrayOf(userId)).use { cursor ->
            if (!cursor.moveToFirst()) return@use null
            FriendStreaks(
                userId = cursor.getStringOrNull("userId")!!,
                notify = cursor.getInteger("notify") == 1,
                expirationTimestamp = cursor.getLongOrNull("expirationTimestamp") ?: 0L,
                length = cursor.getInteger("length")
            )
        }
    }

    fun setFriendStreaksNotify(userId: String, notify: Boolean) {
        executeAsync {
            database.execSQL("UPDATE streaks SET notify = ? WHERE userId = ?", arrayOf(
                if (notify) 1 else 0,
                userId
            ))
        }
    }
}
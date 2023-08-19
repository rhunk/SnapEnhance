package me.rhunk.snapenhance.messaging

import android.database.sqlite.SQLiteDatabase
import me.rhunk.snapenhance.Logger
import me.rhunk.snapenhance.RemoteSideContext
import me.rhunk.snapenhance.core.messaging.FriendStreaks
import me.rhunk.snapenhance.core.messaging.MessagingFriendInfo
import me.rhunk.snapenhance.core.messaging.MessagingGroupInfo
import me.rhunk.snapenhance.core.messaging.MessagingRule
import me.rhunk.snapenhance.core.messaging.Mode
import me.rhunk.snapenhance.core.messaging.RuleScope
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


    fun init() {
        database = context.androidContext.openOrCreateDatabase("main.db", 0, null)
        SQLiteDatabaseHelper.createTablesFromSchema(database, mapOf(
            "friends" to listOf(
                "userId VARCHAR PRIMARY KEY",
                "displayName VARCHAR",
                "mutableUsername VARCHAR",
                "bitmojiId VARCHAR",
                "selfieId VARCHAR"
            ),
            "groups" to listOf(
                "conversationId VARCHAR PRIMARY KEY",
                "name VARCHAR",
                "participantsCount INTEGER"
            ),
            "rules" to listOf(
                "id INTEGER PRIMARY KEY AUTOINCREMENT",
                "scope VARCHAR",
                "targetUuid VARCHAR",
                "enabled BOOLEAN",
                "mode VARCHAR",
                "subject VARCHAR"
            ),
            "streaks" to listOf(
                "userId VARCHAR PRIMARY KEY",
                "notify BOOLEAN",
                "expirationTimestamp BIGINT",
                "count INTEGER"
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

    fun getFriendsIds(): List<String> {
        return database.rawQuery("SELECT userId FROM friends", null).use { cursor ->
            val ids = mutableListOf<String>()
            while (cursor.moveToNext()) {
                ids.add(cursor.getString(0))
            }
            ids
        }
    }

    fun getGroupsIds(): List<String> {
        return database.rawQuery("SELECT conversationId FROM groups", null).use { cursor ->
            val ids = mutableListOf<String>()
            while (cursor.moveToNext()) {
                ids.add(cursor.getString(0))
            }
            ids
        }
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

    fun getFriends(): List<MessagingFriendInfo> {
        return database.rawQuery("SELECT * FROM friends", null).use { cursor ->
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
        executor.execute {
            try {
                database.execSQL("INSERT OR REPLACE INTO groups VALUES (?, ?, ?)", arrayOf(
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
        executor.execute {
            try {
                database.execSQL("INSERT OR REPLACE INTO friends VALUES (?, ?, ?, ?, ?)", arrayOf(
                    friend.userId,
                    friend.displayName,
                    friend.usernameForSorting!!.split("|")[1],
                    friend.bitmojiAvatarId,
                    friend.bitmojiSelfieId
                ))
                //sync streaks
                if (friend.streakLength > 0) {
                    database.execSQL("INSERT OR REPLACE INTO streaks (userId, expirationTimestamp, count) VALUES (?, ?, ?)", arrayOf(
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

    fun getRulesFromId(type: RuleScope, targetUuid: String): List<MessagingRule> {
        return database.rawQuery("SELECT * FROM rules WHERE objectType = ? AND targetUuid = ?", arrayOf(type.name, targetUuid)).use { cursor ->
            val rules = mutableListOf<MessagingRule>()
            while (cursor.moveToNext()) {
                rules.add(MessagingRule(
                    id = cursor.getInteger("id"),
                    ruleScope = RuleScope.valueOf(cursor.getStringOrNull("scope")!!),
                    targetUuid = cursor.getStringOrNull("targetUuid")!!,
                    enabled = cursor.getInteger("enabled") == 1,
                    mode = Mode.valueOf(cursor.getStringOrNull("mode")!!),
                    subject = cursor.getStringOrNull("subject")!!
                ))
            }
            rules
        }
    }

    fun getFriendStreaks(userId: String): FriendStreaks? {
        return database.rawQuery("SELECT * FROM streaks WHERE userId = ?", arrayOf(userId)).use { cursor ->
            if (!cursor.moveToFirst()) return@use null
            FriendStreaks(
                userId = cursor.getStringOrNull("userId")!!,
                notify = cursor.getInteger("notify") == 1,
                expirationTimestamp = cursor.getLongOrNull("expirationTimestamp") ?: 0L,
                count = cursor.getInteger("count")
            )
        }
    }
}
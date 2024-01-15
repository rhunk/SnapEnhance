package me.rhunk.snapenhance.messaging

import android.database.sqlite.SQLiteDatabase
import me.rhunk.snapenhance.RemoteSideContext
import me.rhunk.snapenhance.common.data.FriendStreaks
import me.rhunk.snapenhance.common.data.MessagingFriendInfo
import me.rhunk.snapenhance.common.data.MessagingGroupInfo
import me.rhunk.snapenhance.common.data.MessagingRuleType
import me.rhunk.snapenhance.common.scripting.type.ModuleInfo
import me.rhunk.snapenhance.common.util.SQLiteDatabaseHelper
import me.rhunk.snapenhance.common.util.ktx.getInteger
import me.rhunk.snapenhance.common.util.ktx.getLongOrNull
import me.rhunk.snapenhance.common.util.ktx.getStringOrNull
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
                context.log.error("Failed to execute async block", it)
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
                "type VARCHAR",
                "targetUuid VARCHAR"
            ),
            "streaks" to listOf(
                "id VARCHAR PRIMARY KEY",
                "notify BOOLEAN",
                "expirationTimestamp BIGINT",
                "length INTEGER"
            ),
            "scripts" to listOf(
                "name VARCHAR PRIMARY KEY",
                "version VARCHAR NOT NULL",
                "displayName VARCHAR",
                "description VARCHAR",
                "author VARCHAR NOT NULL",
                "enabled BOOLEAN"
            )
        ))
    }

    fun getGroups(): List<MessagingGroupInfo> {
        return database.rawQuery("SELECT * FROM groups", null).use { cursor ->
            val groups = mutableListOf<MessagingGroupInfo>()
            while (cursor.moveToNext()) {
                groups.add(
                    MessagingGroupInfo(
                        conversationId = cursor.getStringOrNull("conversationId")!!,
                        name = cursor.getStringOrNull("name")!!,
                        participantsCount = cursor.getInteger("participantsCount")
                    )
                )
            }
            groups
        }
    }

    fun getFriends(descOrder: Boolean = false): List<MessagingFriendInfo> {
        return database.rawQuery("SELECT * FROM friends LEFT OUTER JOIN streaks ON friends.userId = streaks.id ORDER BY id ${if (descOrder) "DESC" else "ASC"}", null).use { cursor ->
            val friends = mutableListOf<MessagingFriendInfo>()
            while (cursor.moveToNext()) {
                runCatching {
                    friends.add(
                        MessagingFriendInfo(
                            userId = cursor.getStringOrNull("userId")!!,
                            displayName = cursor.getStringOrNull("displayName"),
                            mutableUsername = cursor.getStringOrNull("mutableUsername")!!,
                            bitmojiId = cursor.getStringOrNull("bitmojiId"),
                            selfieId = cursor.getStringOrNull("selfieId"),
                            streaks = cursor.getLongOrNull("expirationTimestamp")?.let {
                                FriendStreaks(
                                    notify = cursor.getInteger("notify") == 1,
                                    expirationTimestamp = it,
                                    length = cursor.getInteger("length")
                                )
                            }
                        )
                    )
                }.onFailure {
                    context.log.error("Failed to parse friend", it)
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

    fun syncFriend(friend: MessagingFriendInfo) {
        executeAsync {
            try {
                database.execSQL(
                    "INSERT OR REPLACE INTO friends (userId, displayName, mutableUsername, bitmojiId, selfieId) VALUES (?, ?, ?, ?, ?)",
                    arrayOf(
                        friend.userId,
                        friend.displayName,
                        friend.mutableUsername,
                        friend.bitmojiId,
                        friend.selfieId
                    )
                )
                //sync streaks
                friend.streaks?.takeIf { it.length > 0 }?.let {
                    val streaks = getFriendStreaks(friend.userId)

                    database.execSQL("INSERT OR REPLACE INTO streaks (id, notify, expirationTimestamp, length) VALUES (?, ?, ?, ?)", arrayOf(
                        friend.userId,
                        streaks?.notify ?: true,
                        it.expirationTimestamp,
                        it.length
                    ))
                } ?: database.execSQL("DELETE FROM streaks WHERE id = ?", arrayOf(friend.userId))
            } catch (e: Exception) {
                throw e
            }
        }
    }

    fun getRules(targetUuid: String): List<MessagingRuleType> {
        return database.rawQuery("SELECT type FROM rules WHERE targetUuid = ?", arrayOf(
            targetUuid
        )).use { cursor ->
            val rules = mutableListOf<MessagingRuleType>()
            while (cursor.moveToNext()) {
                runCatching {
                    rules.add(MessagingRuleType.getByName(cursor.getStringOrNull("type")!!) ?: return@runCatching)
                }.onFailure {
                    context.log.error("Failed to parse rule", it)
                }
            }
            rules
        }
    }

    fun setRule(targetUuid: String, type: String, enabled: Boolean) {
        executeAsync {
            if (enabled) {
                database.execSQL("INSERT OR REPLACE INTO rules (targetUuid, type) VALUES (?, ?)", arrayOf(
                    targetUuid,
                    type
                ))
            } else {
                database.execSQL("DELETE FROM rules WHERE targetUuid = ? AND type = ?", arrayOf(
                    targetUuid,
                    type
                ))
            }
        }
    }

    fun getFriendInfo(userId: String): MessagingFriendInfo? {
        return database.rawQuery("SELECT * FROM friends LEFT OUTER JOIN streaks ON friends.userId = streaks.id WHERE userId = ?", arrayOf(userId)).use { cursor ->
            if (!cursor.moveToFirst()) return@use null
            MessagingFriendInfo(
                userId = cursor.getStringOrNull("userId")!!,
                displayName = cursor.getStringOrNull("displayName"),
                mutableUsername = cursor.getStringOrNull("mutableUsername")!!,
                bitmojiId = cursor.getStringOrNull("bitmojiId"),
                selfieId = cursor.getStringOrNull("selfieId"),
                streaks = cursor.getLongOrNull("expirationTimestamp")?.let {
                    FriendStreaks(
                        notify = cursor.getInteger("notify") == 1,
                        expirationTimestamp = it,
                        length = cursor.getInteger("length")
                    )
                }
            )
        }
    }

    fun deleteFriend(userId: String) {
        executeAsync {
            database.execSQL("DELETE FROM friends WHERE userId = ?", arrayOf(userId))
            database.execSQL("DELETE FROM streaks WHERE id = ?", arrayOf(userId))
            database.execSQL("DELETE FROM rules WHERE targetUuid = ?", arrayOf(userId))
        }
    }

    fun deleteGroup(conversationId: String) {
        executeAsync {
            database.execSQL("DELETE FROM groups WHERE conversationId = ?", arrayOf(conversationId))
            database.execSQL("DELETE FROM rules WHERE targetUuid = ?", arrayOf(conversationId))
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
        return database.rawQuery("SELECT * FROM streaks WHERE id = ?", arrayOf(userId)).use { cursor ->
            if (!cursor.moveToFirst()) return@use null
            FriendStreaks(
                notify = cursor.getInteger("notify") == 1,
                expirationTimestamp = cursor.getLongOrNull("expirationTimestamp") ?: 0L,
                length = cursor.getInteger("length")
            )
        }
    }

    fun setFriendStreaksNotify(userId: String, notify: Boolean) {
        executeAsync {
            database.execSQL("UPDATE streaks SET notify = ? WHERE id = ?", arrayOf(
                if (notify) 1 else 0,
                userId
            ))
        }
    }

    fun getRuleIds(type: String): MutableList<String> {
        return database.rawQuery("SELECT targetUuid FROM rules WHERE type = ?", arrayOf(type)).use { cursor ->
            val ruleIds = mutableListOf<String>()
            while (cursor.moveToNext()) {
                ruleIds.add(cursor.getStringOrNull("targetUuid")!!)
            }
            ruleIds
        }
    }

    fun getScripts(): List<ModuleInfo> {
        return database.rawQuery("SELECT * FROM scripts", null).use { cursor ->
            val scripts = mutableListOf<ModuleInfo>()
            while (cursor.moveToNext()) {
                scripts.add(
                    ModuleInfo(
                        name = cursor.getStringOrNull("name")!!,
                        version = cursor.getStringOrNull("version")!!,
                        displayName = cursor.getStringOrNull("displayName"),
                        description = cursor.getStringOrNull("description"),
                        author = cursor.getStringOrNull("author"),
                        grantedPermissions = emptyList()
                    )
                )
            }
            scripts
        }
    }

    fun setScriptEnabled(name: String, enabled: Boolean) {
        executeAsync {
            database.execSQL("UPDATE scripts SET enabled = ? WHERE name = ?", arrayOf(
                if (enabled) 1 else 0,
                name
            ))
        }
    }

    fun isScriptEnabled(name: String): Boolean {
        return database.rawQuery("SELECT enabled FROM scripts WHERE name = ?", arrayOf(name)).use { cursor ->
            if (!cursor.moveToFirst()) return@use false
            cursor.getInteger("enabled") == 1
        }
    }

    fun syncScripts(availableScripts: List<ModuleInfo>) {
        executeAsync {
            val enabledScripts = getScripts()
            val enabledScriptPaths = enabledScripts.map { it.name }
            val availableScriptPaths = availableScripts.map { it.name }

            enabledScripts.forEach { script ->
                if (!availableScriptPaths.contains(script.name)) {
                    database.execSQL("DELETE FROM scripts WHERE name = ?", arrayOf(script.name))
                }
            }

            availableScripts.forEach { script ->
                if (!enabledScriptPaths.contains(script.name) || script != enabledScripts.find { it.name == script.name }) {
                    database.execSQL(
                        "INSERT OR REPLACE INTO scripts (name, version, displayName, description, author, enabled) VALUES (?, ?, ?, ?, ?, ?)",
                        arrayOf(
                            script.name,
                            script.version,
                            script.displayName,
                            script.description,
                            script.author,
                            0
                        )
                    )
                }
            }
        }
    }
}
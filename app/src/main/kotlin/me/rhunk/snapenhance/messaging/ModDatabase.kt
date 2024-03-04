package me.rhunk.snapenhance.messaging

import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.runBlocking
import me.rhunk.snapenhance.RemoteSideContext
import me.rhunk.snapenhance.common.data.*
import me.rhunk.snapenhance.common.scripting.type.ModuleInfo
import me.rhunk.snapenhance.common.util.SQLiteDatabaseHelper
import me.rhunk.snapenhance.common.util.ktx.getInteger
import me.rhunk.snapenhance.common.util.ktx.getLongOrNull
import me.rhunk.snapenhance.common.util.ktx.getStringOrNull
import java.util.concurrent.Executors
import kotlin.coroutines.suspendCoroutine


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
            ),
            "tracker_rules" to listOf(
                "id INTEGER PRIMARY KEY AUTOINCREMENT",
                "conversation_id CHAR(36)", // nullable
                "user_id CHAR(36)", // nullable
            ),
            "tracker_rules_events" to listOf(
                "id INTEGER PRIMARY KEY AUTOINCREMENT",
                "rule_id INTEGER",
                "flags INTEGER DEFAULT 1",
                "event_type VARCHAR",
                "params TEXT",
                "actions TEXT"
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

    fun clearTrackerRules() {
        runBlocking {
            suspendCoroutine { continuation ->
                executeAsync {
                    database.execSQL("DELETE FROM tracker_rules")
                    database.execSQL("DELETE FROM tracker_rules_events")
                    continuation.resumeWith(Result.success(Unit))
                }
            }
        }
    }

    fun deleteTrackerRule(ruleId: Int) {
        executeAsync {
            database.execSQL("DELETE FROM tracker_rules WHERE id = ?", arrayOf(ruleId))
            database.execSQL("DELETE FROM tracker_rules_events WHERE rule_id = ?", arrayOf(ruleId))
        }
    }

    fun addTrackerRule(conversationId: String?, userId: String?): Int {
        return runBlocking {
            suspendCoroutine { continuation ->
                executeAsync {
                    val id = database.insert("tracker_rules", null, ContentValues().apply {
                        put("conversation_id", conversationId)
                        put("user_id", userId)
                    })
                    continuation.resumeWith(Result.success(id.toInt()))
                }
            }
        }
    }

    fun addOrUpdateTrackerRuleEvent(
        ruleEventId: Int? = null,
        ruleId: Int? = null,
        eventType: String? = null,
        params: TrackerRuleActionParams,
        actions: List<TrackerRuleAction>
    ): Int? {
        return runBlocking {
            suspendCoroutine { continuation ->
                executeAsync {
                    val id = if (ruleEventId != null) {
                        database.execSQL("UPDATE tracker_rules_events SET params = ?, actions = ? WHERE id = ?", arrayOf(
                            context.gson.toJson(params),
                            context.gson.toJson(actions),
                            ruleEventId
                        ))
                        ruleEventId
                    } else {
                        database.insert("tracker_rules_events", null, ContentValues().apply {
                            put("rule_id", ruleId)
                            put("event_type", eventType)
                            put("params", context.gson.toJson(params))
                            put("actions", context.gson.toJson(actions))
                        }).toInt()
                    }
                    continuation.resumeWith(Result.success(id))
                }
            }
        }
    }

    fun deleteTrackerRuleEvent(eventId: Int) {
        executeAsync {
            database.execSQL("DELETE FROM tracker_rules_events WHERE id = ?", arrayOf(eventId))
        }
    }

     fun getTrackerRules(conversationId: String?, userId: String?): List<TrackerRule> {
        val rules = mutableListOf<TrackerRule>()

        database.rawQuery("SELECT * FROM tracker_rules WHERE (conversation_id = ? OR conversation_id IS NULL) AND (user_id = ? OR user_id IS NULL)", arrayOf(conversationId, userId).filterNotNull().toTypedArray()).use { cursor ->
            while (cursor.moveToNext()) {
                rules.add(
                    TrackerRule(
                        id = cursor.getInteger("id"),
                        conversationId = cursor.getStringOrNull("conversation_id"),
                        userId = cursor.getStringOrNull("user_id"),
                    )
                )
            }
        }
        
        return rules
    }

    fun getTrackerEvents(ruleId: Int): List<TrackerRuleEvent> {
        val events = mutableListOf<TrackerRuleEvent>()
        database.rawQuery("SELECT * FROM tracker_rules_events WHERE rule_id = ?", arrayOf(ruleId.toString())).use { cursor ->
            while (cursor.moveToNext()) {
                events.add(
                    TrackerRuleEvent(
                        id = cursor.getInteger("id"),
                        eventType = cursor.getStringOrNull("event_type") ?: continue,
                        enabled = cursor.getInteger("flags") == 1,
                        params = context.gson.fromJson(cursor.getStringOrNull("params") ?: "{}", TrackerRuleActionParams::class.java),
                        actions = context.gson.fromJson(
                            cursor.getStringOrNull("actions") ?: "[]",
                            object: TypeToken<List<TrackerRuleAction>>() {}.type
                        )
                    )
                )
            }
        }
        return events
    }

    fun getTrackerEvents(eventType: String): Map<TrackerRuleEvent, TrackerRule> {
        val events = mutableMapOf<TrackerRuleEvent, TrackerRule>()
        database.rawQuery("SELECT tracker_rules_events.id as event_id, tracker_rules_events.params as event_params," +
                "tracker_rules_events.actions, tracker_rules_events.flags, tracker_rules_events.event_type, tracker_rules.conversation_id, tracker_rules.user_id " +
                "FROM tracker_rules_events " +
                "INNER JOIN tracker_rules " +
                "ON tracker_rules_events.rule_id = tracker_rules.id " +
                "WHERE event_type = ?", arrayOf(eventType)
        ).use { cursor ->
            while (cursor.moveToNext()) {
                val trackerRule = TrackerRule(
                    id = -1,
                    conversationId = cursor.getStringOrNull("conversation_id"),
                    userId = cursor.getStringOrNull("user_id"),
                )
                val trackerRuleEvent = TrackerRuleEvent(
                    id = cursor.getInteger("event_id"),
                    eventType = cursor.getStringOrNull("event_type") ?: continue,
                    enabled = cursor.getInteger("flags") == 1,
                    params = context.gson.fromJson(cursor.getStringOrNull("event_params") ?: "{}", TrackerRuleActionParams::class.java),
                    actions = context.gson.fromJson(
                        cursor.getStringOrNull("actions") ?: "[]",
                        object: TypeToken<List<TrackerRuleAction>>() {}.type
                    )
                )
                events[trackerRuleEvent] = trackerRule
            }
        }
        return events
    }
}
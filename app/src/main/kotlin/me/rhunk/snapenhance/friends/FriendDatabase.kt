package me.rhunk.snapenhance.friends

import android.database.sqlite.SQLiteDatabase
import me.rhunk.snapenhance.RemoteSideContext
import me.rhunk.snapenhance.util.SQLiteDatabaseHelper

class FriendDatabase(
    private val context: RemoteSideContext,
) {
    private lateinit var database: SQLiteDatabase

    fun init() {
        database = context.androidContext.openOrCreateDatabase("friends.db", 0, null)
        SQLiteDatabaseHelper.createTablesFromSchema(database, mapOf(
            "friends" to listOf(
                "userId VARCHAR PRIMARY KEY",
                "displayName VARCHAR",
                "mutable_username VARCHAR",
                "bitmojiId VARCHAR",
                "selfieId VARCHAR"
            ),
            "rules" to listOf(
                "userId VARCHAR PRIMARY KEY",
                "enabled BOOLEAN",
                "mode VARCHAR",
                "type VARCHAR"
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
}
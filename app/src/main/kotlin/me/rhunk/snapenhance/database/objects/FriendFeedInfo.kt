package me.rhunk.snapenhance.database.objects

import android.annotation.SuppressLint
import android.database.Cursor
import me.rhunk.snapenhance.database.DatabaseObject

data class FriendFeedInfo(
    var id: Int = 0,
    var feedDisplayName: String? = null,
    var participantsSize: Int = 0,
    var lastInteractionTimestamp: Long = 0,
    var displayTimestamp: Long = 0,
    var displayInteractionType: String? = null,
    var lastInteractionUserId: Int = 0,
    var key: String? = null,
    var friendUserId: String? = null,
    var friendDisplayName: String? = null,
) : DatabaseObject {

    @SuppressLint("Range")
    override fun write(cursor: Cursor) {
        id = cursor.getInt(cursor.getColumnIndex("_id"))
        feedDisplayName = cursor.getString(cursor.getColumnIndex("feedDisplayName"))
        participantsSize = cursor.getInt(cursor.getColumnIndex("participantsSize"))
        lastInteractionTimestamp = cursor.getLong(cursor.getColumnIndex("lastInteractionTimestamp"))
        displayTimestamp = cursor.getLong(cursor.getColumnIndex("displayTimestamp"))
        displayInteractionType = cursor.getString(cursor.getColumnIndex("displayInteractionType"))
        lastInteractionUserId = cursor.getInt(cursor.getColumnIndex("lastInteractionUserId"))
        key = cursor.getString(cursor.getColumnIndex("key"))
        friendUserId = cursor.getString(cursor.getColumnIndex("friendUserId"))
        friendDisplayName = cursor.getString(cursor.getColumnIndex("friendDisplayUsername"))
    }
}

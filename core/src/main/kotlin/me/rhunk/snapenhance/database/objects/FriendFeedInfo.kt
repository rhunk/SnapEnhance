package me.rhunk.snapenhance.database.objects

import android.annotation.SuppressLint
import android.database.Cursor
import me.rhunk.snapenhance.database.DatabaseObject
import me.rhunk.snapenhance.util.getInteger
import me.rhunk.snapenhance.util.getLong
import me.rhunk.snapenhance.util.getStringOrNull

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
        with(cursor) {
            id = getInteger("_id")
            feedDisplayName = getStringOrNull("feedDisplayName")
            participantsSize = getInteger("participantsSize")
            lastInteractionTimestamp = getLong("lastInteractionTimestamp")
            displayTimestamp = getLong("displayTimestamp")
            displayInteractionType = getStringOrNull("displayInteractionType")
            lastInteractionUserId = getInteger("lastInteractionUserId")
            key = getStringOrNull("key")
            friendUserId = getStringOrNull("friendUserId")
            friendDisplayName = getStringOrNull("friendDisplayUsername")
        }
    }
}

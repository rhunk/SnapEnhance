package me.rhunk.snapenhance.database.objects

import android.annotation.SuppressLint
import android.database.Cursor
import me.rhunk.snapenhance.database.DatabaseObject
import me.rhunk.snapenhance.util.ktx.getIntOrNull
import me.rhunk.snapenhance.util.ktx.getInteger
import me.rhunk.snapenhance.util.ktx.getLong
import me.rhunk.snapenhance.util.ktx.getStringOrNull

data class FriendFeedEntry(
    var id: Int = 0,
    var feedDisplayName: String? = null,
    var participantsSize: Int = 0,
    var lastInteractionTimestamp: Long = 0,
    var displayTimestamp: Long = 0,
    var displayInteractionType: String? = null,
    var lastInteractionUserId: Int? = null,
    var key: String? = null,
    var friendUserId: String? = null,
    var friendDisplayName: String? = null,
    var friendDisplayUsername: String? = null,
    var friendLinkType: Int? = null,
    var bitmojiAvatarId: String? = null,
    var bitmojiSelfieId: String? = null,
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
            lastInteractionUserId = getIntOrNull("lastInteractionUserId")
            key = getStringOrNull("key")
            friendUserId = getStringOrNull("friendUserId")
            friendDisplayName = getStringOrNull("friendDisplayName")
            friendDisplayUsername = getStringOrNull("friendDisplayUsername")
            friendLinkType = getIntOrNull("friendLinkType")
            bitmojiAvatarId = getStringOrNull("bitmojiAvatarId")
            bitmojiSelfieId = getStringOrNull("bitmojiSelfieId")
        }
    }
}

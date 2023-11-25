package me.rhunk.snapenhance.common.database.impl

import android.annotation.SuppressLint
import android.database.Cursor
import me.rhunk.snapenhance.common.database.DatabaseObject
import me.rhunk.snapenhance.common.util.SerializableDataObject
import me.rhunk.snapenhance.common.util.ktx.getInteger
import me.rhunk.snapenhance.common.util.ktx.getLong
import me.rhunk.snapenhance.common.util.ktx.getStringOrNull

data class FriendInfo(
    var id: Int = 0,
    var lastModifiedTimestamp: Long = 0,
    var username: String? = null,
    var userId: String? = null,
    var displayName: String? = null,
    var bitmojiAvatarId: String? = null,
    var bitmojiSelfieId: String? = null,
    var bitmojiSceneId: String? = null,
    var bitmojiBackgroundId: String? = null,
    var friendmojis: String? = null,
    var friendmojiCategories: String? = null,
    var snapScore: Int = 0,
    var birthday: Long = 0,
    var addedTimestamp: Long = -1,
    var reverseAddedTimestamp: Long = -1,
    var serverDisplayName: String? = null,
    var streakLength: Int = 0,
    var streakExpirationTimestamp: Long = 0,
    var reverseBestFriendRanking: Int = 0,
    var isPinnedBestFriend: Int = 0,
    var plusBadgeVisibility: Int = 0,
    var usernameForSorting: String? = null,
    var friendLinkType: Int = 0,
    var postViewEmoji: String? = null,
) : DatabaseObject, SerializableDataObject() {
    val mutableUsername get() = username?.split("|")?.last()
    val firstCreatedUsername get() = username?.split("|")?.first()

    @SuppressLint("Range")
    override fun write(cursor: Cursor) {
        with(cursor) {
            id = getInteger("_id")
            lastModifiedTimestamp = getLong("_lastModifiedTimestamp")
            username = getStringOrNull("username")
            userId = getStringOrNull("userId")
            displayName = getStringOrNull("displayName")
            bitmojiAvatarId = getStringOrNull("bitmojiAvatarId")
            bitmojiSelfieId = getStringOrNull("bitmojiSelfieId")
            bitmojiSceneId = getStringOrNull("bitmojiSceneId")
            bitmojiBackgroundId = getStringOrNull("bitmojiBackgroundId")
            friendmojis = getStringOrNull("friendmojis")
            friendmojiCategories = getStringOrNull("friendmojiCategories")
            snapScore = getInteger("score")
            birthday = getLong("birthday")
            addedTimestamp = getLong("addedTimestamp")
            reverseAddedTimestamp = getLong("reverseAddedTimestamp")
            serverDisplayName = getStringOrNull("serverDisplayName")
            streakLength = getInteger("streakLength")
            streakExpirationTimestamp = getLong("streakExpiration")
            reverseBestFriendRanking = getInteger("reverseBestFriendRanking")
            usernameForSorting = getStringOrNull("usernameForSorting")
            friendLinkType = getInteger("friendLinkType")
            postViewEmoji = getStringOrNull("postViewEmoji")
            if (getColumnIndex("isPinnedBestFriend") != -1) isPinnedBestFriend =
                getInteger("isPinnedBestFriend")
            if (getColumnIndex("plusBadgeVisibility") != -1) plusBadgeVisibility =
                getInteger("plusBadgeVisibility")
        }
    }

}

package me.rhunk.snapenhance.database.objects

import android.annotation.SuppressLint
import android.database.Cursor
import me.rhunk.snapenhance.database.DatabaseObject

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
    var addedTimestamp: Long = 0,
    var reverseAddedTimestamp: Long = 0,
    var serverDisplayName: String? = null,
    var streakLength: Int = 0,
    var streakExpirationTimestamp: Long = 0,
    var reverseBestFriendRanking: Int = 0,
    var isPinnedBestFriend: Int = 0,
    var plusBadgeVisibility: Int = 0,
    var usernameForSorting: String? = null
) : DatabaseObject {
    @SuppressLint("Range")
    override fun write(cursor: Cursor) {
        id = cursor.getInt(cursor.getColumnIndex("_id"))
        lastModifiedTimestamp = cursor.getLong(cursor.getColumnIndex("_lastModifiedTimestamp"))
        username = cursor.getString(cursor.getColumnIndex("username"))
        userId = cursor.getString(cursor.getColumnIndex("userId"))
        displayName = cursor.getString(cursor.getColumnIndex("displayName"))
        bitmojiAvatarId = cursor.getString(cursor.getColumnIndex("bitmojiAvatarId"))
        bitmojiSelfieId = cursor.getString(cursor.getColumnIndex("bitmojiSelfieId"))
        bitmojiSceneId = cursor.getString(cursor.getColumnIndex("bitmojiSceneId"))
        bitmojiBackgroundId = cursor.getString(cursor.getColumnIndex("bitmojiBackgroundId"))
        friendmojis = cursor.getString(cursor.getColumnIndex("friendmojis"))
        friendmojiCategories = cursor.getString(cursor.getColumnIndex("friendmojiCategories"))
        snapScore = cursor.getInt(cursor.getColumnIndex("score"))
        birthday = cursor.getLong(cursor.getColumnIndex("birthday"))
        addedTimestamp = cursor.getLong(cursor.getColumnIndex("addedTimestamp"))
        reverseAddedTimestamp = cursor.getLong(cursor.getColumnIndex("reverseAddedTimestamp"))
        serverDisplayName = cursor.getString(cursor.getColumnIndex("serverDisplayName"))
        streakLength = cursor.getInt(cursor.getColumnIndex("streakLength"))
        streakExpirationTimestamp = cursor.getLong(cursor.getColumnIndex("streakExpiration"))
        reverseBestFriendRanking = cursor.getInt(cursor.getColumnIndex("reverseBestFriendRanking"))
        usernameForSorting = cursor.getString(cursor.getColumnIndex("usernameForSorting"))
        if (cursor.getColumnIndex("isPinnedBestFriend") != -1) isPinnedBestFriend =
            cursor.getInt(cursor.getColumnIndex("isPinnedBestFriend"))
        if (cursor.getColumnIndex("plusBadgeVisibility") != -1) plusBadgeVisibility =
            cursor.getInt(cursor.getColumnIndex("plusBadgeVisibility"))
    }
}

package me.rhunk.snapenhance.database.objects

import android.annotation.SuppressLint
import android.database.Cursor
import me.rhunk.snapenhance.database.DatabaseObject
import me.rhunk.snapenhance.util.ktx.getInteger
import me.rhunk.snapenhance.util.ktx.getStringOrNull

data class StoryEntry(
    var id: Int = 0,
    var storyId: String? = null,
    var displayName: String? = null,
    var isLocal: Boolean? = null,
    var userId: String? = null
) : DatabaseObject {

    @SuppressLint("Range")
    override fun write(cursor: Cursor) {
        with(cursor) {
            id = getInteger("_id")
            storyId = getStringOrNull("storyId")
            displayName = getStringOrNull("displayName")
            isLocal = getInteger("isLocal") == 1
            userId = getStringOrNull("userId")
        }
    }
}

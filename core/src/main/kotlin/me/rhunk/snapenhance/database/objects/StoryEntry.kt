package me.rhunk.snapenhance.database.objects

import android.annotation.SuppressLint
import android.database.Cursor
import me.rhunk.snapenhance.database.DatabaseObject

data class StoryEntry(
    var id: Int = 0,
    var storyId: String? = null,
    var displayName: String? = null,
    var isLocal: Boolean? = null,
    var userId: String? = null
) : DatabaseObject {

    @SuppressLint("Range")
    override fun write(cursor: Cursor) {
        id = cursor.getInt(cursor.getColumnIndex("_id"))
        storyId = cursor.getString(cursor.getColumnIndex("storyId"))
        displayName = cursor.getString(cursor.getColumnIndex("displayName"))
        isLocal = cursor.getInt(cursor.getColumnIndex("isLocal")) == 1
        userId = cursor.getString(cursor.getColumnIndex("userId"))
    }
}

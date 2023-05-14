package me.rhunk.snapenhance.database.objects

import android.annotation.SuppressLint
import android.database.Cursor
import me.rhunk.snapenhance.database.DatabaseObject

class UserConversationLink(
    var user_id: String? = null,
    var client_conversation_id: String? = null,
    var conversation_type: Int = 0
) : DatabaseObject {

    @SuppressLint("Range")
    override fun write(cursor: Cursor) {
        user_id = cursor.getString(cursor.getColumnIndex("user_id"))
        client_conversation_id = cursor.getString(cursor.getColumnIndex("client_conversation_id"))
        conversation_type = cursor.getInt(cursor.getColumnIndex("conversation_type"))
    }
}

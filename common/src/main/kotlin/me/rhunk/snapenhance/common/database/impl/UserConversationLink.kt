package me.rhunk.snapenhance.common.database.impl

import android.annotation.SuppressLint
import android.database.Cursor
import me.rhunk.snapenhance.common.database.DatabaseObject
import me.rhunk.snapenhance.common.util.ktx.getInteger
import me.rhunk.snapenhance.common.util.ktx.getStringOrNull

class UserConversationLink(
    var userId: String? = null,
    var clientConversationId: String? = null,
    var conversationType: Int = 0
) : DatabaseObject {

    @SuppressLint("Range")
    override fun write(cursor: Cursor) {
        with(cursor) {
            userId = getStringOrNull("user_id")
            clientConversationId = getStringOrNull("client_conversation_id")
            conversationType = getInteger("conversation_type")
        }
    }
}

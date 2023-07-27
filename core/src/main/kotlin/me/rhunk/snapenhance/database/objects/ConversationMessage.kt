package me.rhunk.snapenhance.database.objects

import android.annotation.SuppressLint
import android.database.Cursor
import me.rhunk.snapenhance.Constants
import me.rhunk.snapenhance.data.ContentType
import me.rhunk.snapenhance.database.DatabaseObject
import me.rhunk.snapenhance.util.protobuf.ProtoReader

@Suppress("ArrayInDataClass")
data class ConversationMessage(
    var client_conversation_id: String? = null,
    var client_message_id: Int = 0,
    var server_message_id: Int = 0,
    var message_content: ByteArray? = null,
    var is_saved: Int = 0,
    var is_viewed_by_user: Int = 0,
    var content_type: Int = 0,
    var creation_timestamp: Long = 0,
    var read_timestamp: Long = 0,
    var sender_id: String? = null
) : DatabaseObject {

    @SuppressLint("Range")
    override fun write(cursor: Cursor) {
        client_conversation_id = cursor.getString(cursor.getColumnIndex("client_conversation_id"))
        client_message_id = cursor.getInt(cursor.getColumnIndex("client_message_id"))
        server_message_id = cursor.getInt(cursor.getColumnIndex("server_message_id"))
        message_content = cursor.getBlob(cursor.getColumnIndex("message_content"))
        is_saved = cursor.getInt(cursor.getColumnIndex("is_saved"))
        is_viewed_by_user = cursor.getInt(cursor.getColumnIndex("is_viewed_by_user"))
        content_type = cursor.getInt(cursor.getColumnIndex("content_type"))
        creation_timestamp = cursor.getLong(cursor.getColumnIndex("creation_timestamp"))
        read_timestamp = cursor.getLong(cursor.getColumnIndex("read_timestamp"))
        sender_id = cursor.getString(cursor.getColumnIndex("sender_id"))
    }

    fun getMessageAsString(): String? {
        return when (ContentType.fromId(content_type)) {
            ContentType.CHAT -> message_content?.let { ProtoReader(it).getString(*Constants.ARROYO_STRING_CHAT_MESSAGE_PROTO) }
            else -> null
        }
    }
}

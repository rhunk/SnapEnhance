package me.rhunk.snapenhance.database.objects

import android.annotation.SuppressLint
import android.database.Cursor
import me.rhunk.snapenhance.Constants
import me.rhunk.snapenhance.data.ContentType
import me.rhunk.snapenhance.database.DatabaseObject
import me.rhunk.snapenhance.util.ktx.getBlobOrNull
import me.rhunk.snapenhance.util.ktx.getInteger
import me.rhunk.snapenhance.util.ktx.getLong
import me.rhunk.snapenhance.util.ktx.getStringOrNull
import me.rhunk.snapenhance.util.protobuf.ProtoReader

@Suppress("ArrayInDataClass")
data class ConversationMessage(
    var clientConversationId: String? = null,
    var clientMessageId: Int = 0,
    var serverMessageId: Int = 0,
    var messageContent: ByteArray? = null,
    var isSaved: Int = 0,
    var isViewedByUser: Int = 0,
    var contentType: Int = 0,
    var creationTimestamp: Long = 0,
    var readTimestamp: Long = 0,
    var senderId: String? = null
) : DatabaseObject {

    @SuppressLint("Range")
    override fun write(cursor: Cursor) {
        with(cursor) {
            clientConversationId = getStringOrNull("client_conversation_id")
            clientMessageId = getInteger("client_message_id")
            serverMessageId = getInteger("server_message_id")
            messageContent = getBlobOrNull("message_content")
            isSaved = getInteger("is_saved")
            isViewedByUser = getInteger("is_viewed_by_user")
            contentType = getInteger("content_type")
            creationTimestamp = getLong("creation_timestamp")
            readTimestamp = getLong("read_timestamp")
            senderId = getStringOrNull("sender_id")
        }
    }

    fun getMessageAsString(): String? {
        return when (ContentType.fromId(contentType)) {
            ContentType.CHAT -> messageContent?.let { ProtoReader(it).getString(*Constants.ARROYO_STRING_CHAT_MESSAGE_PROTO) }
            else -> null
        }
    }
}

package me.rhunk.snapenhance.common.data


data class FriendPresenceState(
    val bitmojiPresent: Boolean,
    val typing: Boolean,
    val wasTyping: Boolean,
    val speaking: Boolean,
    val peeking: Boolean
)

open class SessionEvent(
    val type: SessionEventType,
    val conversationId: String,
    val authorUserId: String,
)

class SessionMessageEvent(
    type: SessionEventType,
    conversationId: String,
    authorUserId: String,
    val serverMessageId: Long,
    val messageData: ByteArray? = null,
    val reactionId: Int? = null,
) : SessionEvent(type, conversationId, authorUserId)


enum class SessionEventType(
    val key: String
) {
    MESSAGE_READ_RECEIPTS("message_read_receipts"),
    MESSAGE_DELETED("message_deleted"),
    MESSAGE_SAVED("message_saved"),
    MESSAGE_UNSAVED("message_unsaved"),
    MESSAGE_REACTION_ADD("message_reaction_add"),
    MESSAGE_REACTION_REMOVE("message_reaction_remove"),
    SNAP_OPENED("snap_opened"),
    SNAP_REPLAYED("snap_replayed"),
    SNAP_REPLAYED_TWICE("snap_replayed_twice"),
    SNAP_SCREENSHOT("snap_screenshot"),
    SNAP_SCREEN_RECORD("snap_screen_record"),
}

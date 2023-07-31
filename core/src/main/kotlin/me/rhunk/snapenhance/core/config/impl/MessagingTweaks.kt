package me.rhunk.snapenhance.core.config.impl

import me.rhunk.snapenhance.core.config.ConfigContainer
import me.rhunk.snapenhance.data.NotificationType

class MessagingTweaks : ConfigContainer() {
    val anonymousStoryViewing = boolean("annonymous_story_viewing")
    val preventReadReceipts = boolean("prevent_read_receipts")
    val hideBitmojiPresence = boolean("hide_bitmoji_presence")
    val hideTypingNotifications = boolean("hide_typing_notifications")
    val unlimitedSnapViewTime = boolean("unlimited_snap_view_time")
    val preventMessageSending = multiple("prevent_message_sending", *NotificationType.getOutgoingValues().map { it.key }.toTypedArray())
    val messageLogger = boolean("message_logger")
    val autoSaveMessagesInConversations = multiple("auto_save_messages_in_conversations",
        "CHAT",
        "SNAP",
        "NOTE",
        "EXTERNAL_MEDIA",
        "STICKER"
    )

    val galleryMediaSendOverride = unique("gallery_media_send_override",  "NOTE", "SNAP", "LIVE_SNAP")
    val messagePreviewLength = integer("message_preview_length", defaultValue = 20)

}
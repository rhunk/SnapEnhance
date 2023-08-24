package me.rhunk.snapenhance.core.config.impl

import me.rhunk.snapenhance.core.config.ConfigContainer
import me.rhunk.snapenhance.core.config.FeatureNotice
import me.rhunk.snapenhance.data.NotificationType

class MessagingTweaks : ConfigContainer() {
    val anonymousStoryViewing = boolean("anonymous_story_viewing")
    val hideBitmojiPresence = boolean("hide_bitmoji_presence")
    val hideTypingNotifications = boolean("hide_typing_notifications")
    val unlimitedSnapViewTime = boolean("unlimited_snap_view_time")
    val autoSaveMessagesInConversations = multiple("auto_save_messages_in_conversations",
        "CHAT",
        "SNAP",
        "NOTE",
        "EXTERNAL_MEDIA",
        "STICKER"
    )
    val preventMessageSending = multiple("prevent_message_sending", *NotificationType.getOutgoingValues().map { it.key }.toTypedArray()) {
        customOptionTranslationPath = "features.options.notifications"
    }
    val messageLogger = boolean("message_logger") { addNotices(FeatureNotice.MAY_CAUSE_CRASHES) }
    val galleryMediaSendOverride = boolean("gallery_media_send_override")
    val messagePreviewLength = integer("message_preview_length", defaultValue = 20)
}
package me.rhunk.snapenhance.common.config.impl

import me.rhunk.snapenhance.common.config.ConfigContainer
import me.rhunk.snapenhance.common.config.FeatureNotice
import me.rhunk.snapenhance.common.config.PropertyValue
import me.rhunk.snapenhance.common.data.NotificationType

class MessagingTweaks : ConfigContainer() {
    inner class HalfSwipeNotifierConfig : ConfigContainer(hasGlobalState = true) {
        val minDuration: PropertyValue<Int> = integer("min_duration", defaultValue = 0) {
            inputCheck = { it.toIntOrNull()?.coerceAtLeast(0) != null && maxDuration.get() >= it.toInt() }
        }
        val maxDuration: PropertyValue<Int> = integer("max_duration", defaultValue = 20) {
            inputCheck = { it.toIntOrNull()?.coerceAtLeast(0) != null && minDuration.get() <= it.toInt() }
        }
    }

    val bypassScreenshotDetection = boolean("bypass_screenshot_detection") { requireRestart() }
    val anonymousStoryViewing = boolean("anonymous_story_viewing")
    val preventStoryRewatchIndicator = boolean("prevent_story_rewatch_indicator") { requireRestart() }
    val hidePeekAPeek = boolean("hide_peek_a_peek")
    val hideBitmojiPresence = boolean("hide_bitmoji_presence")
    val hideTypingNotifications = boolean("hide_typing_notifications")
    val unlimitedSnapViewTime = boolean("unlimited_snap_view_time")
    val disableReplayInFF = boolean("disable_replay_in_ff")
    val halfSwipeNotifier = container("half_swipe_notifier", HalfSwipeNotifierConfig()) { requireRestart()}
    val messagePreviewLength = integer("message_preview_length", defaultValue = 20)
    val callStartConfirmation = boolean("call_start_confirmation") { requireRestart() }
    val autoSaveMessagesInConversations = multiple("auto_save_messages_in_conversations",
        "CHAT",
        "SNAP",
        "NOTE",
        "EXTERNAL_MEDIA",
        "STICKER"
    ) { requireRestart(); customOptionTranslationPath = "content_type" }
    val preventMessageSending = multiple("prevent_message_sending", *NotificationType.getOutgoingValues().map { it.key }.toTypedArray()) {
        customOptionTranslationPath = "features.options.notifications"
        nativeHooks()
    }
    val instantDelete = boolean("instant_delete") { requireRestart() }
    val betterNotifications = multiple("better_notifications", "chat_preview", "media_preview", "reply_button", "download_button", "mark_as_read_button", "mark_as_read_and_save_in_chat", "group") { requireRestart() }
    val notificationBlacklist = multiple("notification_blacklist", *NotificationType.getIncomingValues().map { it.key }.toTypedArray()) {
        customOptionTranslationPath = "features.options.notifications"
    }
    val messageLogger = boolean("message_logger") { addNotices(FeatureNotice.UNSTABLE); requireRestart() }
    val galleryMediaSendOverride = boolean("gallery_media_send_override") { nativeHooks() }
    val stripMediaMetadata = multiple("strip_media_metadata", "hide_caption_text", "hide_snap_filters", "hide_extras", "remove_audio_note_duration", "remove_audio_note_transcript_capability") { requireRestart() }
    val bypassMessageRetentionPolicy = boolean("bypass_message_retention_policy") { addNotices(FeatureNotice.UNSTABLE); requireRestart() }
}
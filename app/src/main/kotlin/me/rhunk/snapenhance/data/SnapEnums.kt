package me.rhunk.snapenhance.data

enum class MessageState {
    PREPARING, SENDING, COMMITTED, FAILED, CANCELING
}

enum class NotificationType (
    val key: String,
    val isIncoming: Boolean = false,
    val associatedOutgoingContentType: ContentType? = null,
) {
    SCREENSHOT("chat_screenshot", true, ContentType.STATUS_CONVERSATION_CAPTURE_SCREENSHOT),
    SCREEN_RECORD("chat_screen_record", true, ContentType.STATUS_CONVERSATION_CAPTURE_RECORD),
    CAMERA_ROLL_SAVE("camera_roll_save", true, ContentType.STATUS_SAVE_TO_CAMERA_ROLL),
    SNAP("snap",true),
    CHAT("chat",true),
    CHAT_REPLY("chat_reply",true),
    TYPING("typing", true),
    STORIES("stories",true),
    INITIATE_AUDIO("initiate_audio",true),
    ABANDON_AUDIO("abandon_audio", false, ContentType.STATUS_CALL_MISSED_AUDIO),
    INITIATE_VIDEO("initiate_video",true),
    ABANDON_VIDEO("abandon_video", false, ContentType.STATUS_CALL_MISSED_VIDEO);

    companion object {
        fun getIncomingValues(): List<NotificationType> {
            return values().filter { it.isIncoming }.toList()
        }

        fun getOutgoingValues(): List<NotificationType> {
            return values().filter { it.associatedOutgoingContentType != null }.toList()
        }

        fun fromContentType(contentType: ContentType): NotificationType? {
            return values().firstOrNull { it.associatedOutgoingContentType == contentType }
        }
    }
}

enum class ContentType(val id: Int) {
    UNKNOWN(-1),
    SNAP(0),
    CHAT(1),
    EXTERNAL_MEDIA(2),
    SHARE(3),
    NOTE(4),
    STICKER(5),
    STATUS(6),
    LOCATION(7),
    STATUS_SAVE_TO_CAMERA_ROLL(8),
    STATUS_CONVERSATION_CAPTURE_SCREENSHOT(9),
    STATUS_CONVERSATION_CAPTURE_RECORD(10),
    STATUS_CALL_MISSED_VIDEO(11),
    STATUS_CALL_MISSED_AUDIO(12),
    LIVE_LOCATION_SHARE(13),
    CREATIVE_TOOL_ITEM(14),
    FAMILY_CENTER_INVITE(15),
    FAMILY_CENTER_ACCEPT(16),
    FAMILY_CENTER_LEAVE(17),
    STATUS_PLUS_GIFT(18);

    companion object {
        fun fromId(i: Int): ContentType {
            return values().firstOrNull { it.id == i } ?: UNKNOWN
        }
    }
}

enum class PlayableSnapState {
    NOTDOWNLOADED, DOWNLOADING, DOWNLOADFAILED, PLAYABLE, VIEWEDREPLAYABLE, PLAYING, VIEWEDNOTREPLAYABLE
}

enum class MediaReferenceType {
    UNASSIGNED, OVERLAY, IMAGE, VIDEO, ASSET_BUNDLE, AUDIO, ANIMATED_IMAGE, FONT, WEB_VIEW_CONTENT, VIDEO_NO_AUDIO
}

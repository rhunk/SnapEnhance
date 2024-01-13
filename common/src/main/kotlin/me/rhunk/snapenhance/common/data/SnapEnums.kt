package me.rhunk.snapenhance.common.data

import me.rhunk.snapenhance.common.util.protobuf.ProtoReader

enum class MessageState {
    PREPARING, SENDING, COMMITTED, FAILED, CANCELING
}

enum class NotificationType (
    val key: String,
    val isIncoming: Boolean = false,
    val associatedOutgoingContentType: ContentType? = null,
    private vararg val aliases: String
) {
    SCREENSHOT("chat_screenshot", true, ContentType.STATUS_CONVERSATION_CAPTURE_SCREENSHOT),
    SCREEN_RECORD("chat_screen_record", true, ContentType.STATUS_CONVERSATION_CAPTURE_RECORD),
    CAMERA_ROLL_SAVE("camera_roll_save", true, ContentType.STATUS_SAVE_TO_CAMERA_ROLL),
    SNAP_REPLAY("snap_replay", true, ContentType.STATUS),
    SNAP("snap",true),
    CHAT("chat",true),
    CHAT_REPLY("chat_reply",true),
    TYPING("typing", true),
    STORIES("stories",true),
    DM_REACTION("chat_reaction", true, null,"snap_reaction", "voicenote_reaction"),
    GROUP_REACTION("group_chat_reaction", true, null,"group_snap_reaction", "group_voicenote_reaction"),
    INITIATE_AUDIO("initiate_audio",true),
    ABANDON_AUDIO("abandon_audio", false, ContentType.STATUS_CALL_MISSED_AUDIO),
    INITIATE_VIDEO("initiate_video",true),
    ABANDON_VIDEO("abandon_video", false, ContentType.STATUS_CALL_MISSED_VIDEO);

    fun isMatch(key: String): Boolean {
        return this.key == key || aliases.contains(key)
    }

    companion object {
        fun getByKey(key: String): NotificationType? {
            return entries.firstOrNull { it.key == key }
        }

        fun getIncomingValues(): List<NotificationType> {
            return entries.filter { it.isIncoming }.toList()
        }

        fun getOutgoingValues(): List<NotificationType> {
            return entries.filter { it.associatedOutgoingContentType != null }.toList()
        }

        fun fromContentType(contentType: ContentType): NotificationType? {
            return entries.firstOrNull { it.associatedOutgoingContentType == contentType }
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
    STATUS_PLUS_GIFT(18),
    TINY_SNAP(19),
    STATUS_COUNTDOWN(20);

    companion object {
        fun fromId(i: Int): ContentType {
            return entries.firstOrNull { it.id == i } ?: UNKNOWN
        }

        fun fromMessageContainer(protoReader: ProtoReader?): ContentType? {
            if (protoReader == null) return null
            return protoReader.run {
                when {
                    contains(8) -> STATUS
                    contains(2) -> CHAT
                    contains(11) -> SNAP
                    contains(6) -> NOTE
                    contains(3) -> EXTERNAL_MEDIA
                    contains(4) -> STICKER
                    contains(5) -> SHARE
                    contains(7) -> EXTERNAL_MEDIA // story replies
                    else -> null
                }
            }
        }
    }
}

enum class PlayableSnapState {
    NOTDOWNLOADED, DOWNLOADING, DOWNLOADFAILED, PLAYABLE, VIEWEDREPLAYABLE, PLAYING, VIEWEDNOTREPLAYABLE
}

enum class MediaReferenceType {
    UNASSIGNED, OVERLAY, IMAGE, VIDEO, ASSET_BUNDLE, AUDIO, ANIMATED_IMAGE, FONT, WEB_VIEW_CONTENT, VIDEO_NO_AUDIO
}


enum class MessageUpdate(
    val key: String,
) {
    UNKNOWN("unknown"),
    READ("read"),
    RELEASE("release"),
    SAVE("save"),
    UNSAVE("unsave"),
    ERASE("erase"),
    SCREENSHOT("screenshot"),
    SCREEN_RECORD("screen_record"),
    REPLAY("replay"),
    REACTION("reaction"),
    REMOVEREACTION("remove_reaction"),
    REVOKETRANSCRIPTION("revoke_transcription"),
    ALLOWTRANSCRIPTION("allow_transcription"),
    ERASESAVEDSTORYMEDIA("erase_saved_story_media"),
}

enum class FriendLinkType(val value: Int, val shortName: String) {
    MUTUAL(0, "mutual"),
    OUTGOING(1, "outgoing"),
    BLOCKED(2, "blocked"),
    DELETED(3, "deleted"),
    FOLLOWING(4, "following"),
    SUGGESTED(5, "suggested"),
    INCOMING(6, "incoming"),
    INCOMING_FOLLOWER(7, "incoming_follower");

    companion object {
        fun fromValue(value: Int): FriendLinkType {
            return entries.firstOrNull { it.value == value } ?: MUTUAL
        }
    }
}

enum class MixerStoryType(
    val index: Int,
) {
    UNKNOWN(-1),
    SUBSCRIPTIONS(2),
    DISCOVER(3),
    FRIENDS(5),
    MY_STORIES(6);

    companion object {
        fun fromIndex(index: Int): MixerStoryType {
            return entries.firstOrNull { it.index == index } ?: UNKNOWN
        }
    }
}
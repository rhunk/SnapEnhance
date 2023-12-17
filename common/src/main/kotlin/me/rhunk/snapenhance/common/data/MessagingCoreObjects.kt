package me.rhunk.snapenhance.common.data

import me.rhunk.snapenhance.common.util.SerializableDataObject


enum class RuleState(
    val key: String
) {
    BLACKLIST("blacklist"),
    WHITELIST("whitelist");

    companion object {
        fun getByName(name: String) = entries.first { it.key == name }
    }
}

enum class SocialScope(
    val key: String,
    val tabRoute: String,
) {
    FRIEND("friend", "friend_info/{id}"),
    GROUP("group", "group_info/{id}");

    companion object {
        fun getByName(name: String) = entries.first { it.key == name }
    }
}

enum class MessagingRuleType(
    val key: String,
    val listMode: Boolean,
    val showInFriendMenu: Boolean = true
) {
    STEALTH("stealth", true),
    AUTO_DOWNLOAD("auto_download", true),
    AUTO_SAVE("auto_save", true),
    HIDE_FRIEND_FEED("hide_friend_feed", false, showInFriendMenu = false),
    E2E_ENCRYPTION("e2e_encryption", false),
    PIN_CONVERSATION("pin_conversation", false, showInFriendMenu = false);

    fun translateOptionKey(optionKey: String): String {
        return if (listMode) "rules.properties.$key.options.$optionKey" else "rules.properties.$key.name"
    }

    companion object {
        fun getByName(name: String) = entries.firstOrNull { it.key == name }
    }
}

data class FriendStreaks(
    val userId: String,
    val notify: Boolean,
    val expirationTimestamp: Long,
    val length: Int
) : SerializableDataObject() {
    fun hoursLeft() = (expirationTimestamp - System.currentTimeMillis()) / 1000 / 60 / 60

    fun isAboutToExpire(expireHours: Int) = expirationTimestamp - System.currentTimeMillis() < expireHours * 60 * 60 * 1000
}

data class MessagingGroupInfo(
    val conversationId: String,
    val name: String,
    val participantsCount: Int
) : SerializableDataObject()

data class MessagingFriendInfo(
    val userId: String,
    val displayName: String?,
    val mutableUsername: String,
    val bitmojiId: String?,
    val selfieId: String?
) : SerializableDataObject()


class StoryData(
    val url: String,
    val postedAt: Long,
    val createdAt: Long,
    val key: ByteArray?,
    val iv: ByteArray?
) : SerializableDataObject()
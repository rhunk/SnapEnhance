package me.rhunk.snapenhance.core.messaging

import me.rhunk.snapenhance.core.util.SerializableDataObject


enum class RuleState(
    val key: String
) {
    BLACKLIST("blacklist"),
    WHITELIST("whitelist");

    companion object {
        fun getByName(name: String) = values().first { it.key == name }
    }
}

enum class SocialScope(
    val key: String,
    val tabRoute: String,
) {
    FRIEND("friend", "friend_info/{id}"),
    GROUP("group", "group_info/{id}"),
}

enum class MessagingRuleType(
    val key: String,
    val listMode: Boolean
) {
    AUTO_DOWNLOAD("auto_download", true),
    STEALTH("stealth", true),
    AUTO_SAVE("auto_save", true),
    HIDE_CHAT_FEED("hide_chat_feed", false);

    fun translateOptionKey(optionKey: String): String {
        return "rules.properties.${key}.options.${optionKey}"
    }

    companion object {
        fun getByName(name: String) = values().first { it.key == name }
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

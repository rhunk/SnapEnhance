package me.rhunk.snapenhance.core.messaging

import me.rhunk.snapenhance.util.SerializableDataObject


enum class Mode {
    BLACKLIST,
    WHITELIST
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
    val socialScope: SocialScope,
) {
    DOWNLOAD("download", SocialScope.FRIEND),
    STEALTH("stealth", SocialScope.GROUP),
    AUTO_SAVE("auto_save", SocialScope.GROUP);
}

data class FriendStreaks(
    val userId: String,
    val notify: Boolean,
    val expirationTimestamp: Long,
    val length: Int
) : SerializableDataObject()


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


data class MessagingRule(
    val id: Int,
    val socialScope: SocialScope,
    val targetUuid: String,
    //val mode: Mode?,
    val subject: String
) : SerializableDataObject()
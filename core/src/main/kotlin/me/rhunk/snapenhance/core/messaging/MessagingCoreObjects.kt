package me.rhunk.snapenhance.core.messaging

import me.rhunk.snapenhance.util.SerializableDataObject


enum class Mode {
    BLACKLIST,
    WHITELIST
}

enum class RuleScope {
    FRIEND,
    GROUP
}

enum class ConversationFeature(
    val value: String,
    val ruleScope: RuleScope,
) {
    DOWNLOAD("download", RuleScope.FRIEND),
    STEALTH("stealth", RuleScope.GROUP),
    AUTO_SAVE("auto_save", RuleScope.GROUP);
}

data class FriendStreaks(
    val userId: String,
    val notify: Boolean,
    val expirationTimestamp: Long,
    val count: Int
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
    val ruleScope: RuleScope,
    val targetUuid: String,
    val enabled: Boolean,
    val mode: Mode?,
    val subject: String
) : SerializableDataObject()
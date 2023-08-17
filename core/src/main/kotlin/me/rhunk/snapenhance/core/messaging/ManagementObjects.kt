package me.rhunk.snapenhance.core.messaging


enum class Mode {
    BLACKLIST,
    WHITELIST
}

enum class ObjectType {
    USER,
    CONVERSATION
}

data class FriendStreaks(
    val userId: String,
    val notify: Boolean,
    val expirationTimestamp: Long,
    val count: Int
)

data class MessagingRule(
    val id: Int,
    val objectType: ObjectType,
    val targetUuid: String,
    val enabled: Boolean,
    val mode: Mode?,
    val subject: String
)
package me.rhunk.snapenhance.core.messaging

enum class EnumBulkAction(
    val key: String,
) {
    REMOVE_FRIENDS("remove_friends"),
    CLEAR_CONVERSATIONS("clear_conversations"),
}
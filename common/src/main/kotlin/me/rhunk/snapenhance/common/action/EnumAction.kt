package me.rhunk.snapenhance.common.action



enum class EnumAction(
    val key: String,
    val exitOnFinish: Boolean = false,
) {
    EXPORT_CHAT_MESSAGES("export_chat_messages"),
    EXPORT_MEMORIES("export_memories"),
    BULK_MESSAGING_ACTION("bulk_messaging_action"),
    MANAGE_FRIEND_LIST("manage_friend_list"),
    CLEAN_CACHE("clean_snapchat_cache", exitOnFinish = true);

    companion object {
        const val ACTION_PARAMETER = "se_action"
    }
}
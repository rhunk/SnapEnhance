package me.rhunk.snapenhance.common.action



enum class EnumAction(
    val key: String,
    val exitOnFinish: Boolean = false,
    val isCritical: Boolean = false,
) {
    CLEAN_CACHE("clean_snapchat_cache", exitOnFinish = true),
    EXPORT_CHAT_MESSAGES("export_chat_messages"),
    BULK_MESSAGING_ACTION("bulk_messaging_action");

    companion object {
        const val ACTION_PARAMETER = "se_action"
    }
}
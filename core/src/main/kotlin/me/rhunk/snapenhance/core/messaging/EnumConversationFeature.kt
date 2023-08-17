package me.rhunk.snapenhance.core.messaging

enum class EnumConversationFeature(
    val value: String,
    val objectType: ObjectType,
) {
    DOWNLOAD("download", ObjectType.USER),
    STEALTH("stealth", ObjectType.CONVERSATION),
    AUTO_SAVE("auto_save", ObjectType.CONVERSATION);
}
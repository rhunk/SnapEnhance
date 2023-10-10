package me.rhunk.snapenhance.common.data.download

enum class MediaDownloadSource(
    val key: String,
    val displayName: String = key,
    val pathName: String = key,
    val ignoreFilter: Boolean = false
) {
    NONE("none", "None", ignoreFilter = true),
    PENDING("pending", "Pending", ignoreFilter = true),
    CHAT_MEDIA("chat_media", "Chat Media", "chat_media"),
    STORY("story", "Story", "story"),
    PUBLIC_STORY("public_story", "Public Story", "public_story"),
    SPOTLIGHT("spotlight", "Spotlight", "spotlight"),
    PROFILE_PICTURE("profile_picture", "Profile Picture", "profile_picture");

    fun matches(source: String?): Boolean {
        if (source == null) return false
        return source.contains(key, ignoreCase = true)
    }

    companion object {
        fun fromKey(key: String?): MediaDownloadSource {
            if (key == null) return NONE
            return entries.find { it.key == key } ?: NONE
        }
    }
}
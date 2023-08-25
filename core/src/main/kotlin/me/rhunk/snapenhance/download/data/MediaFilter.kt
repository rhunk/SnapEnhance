package me.rhunk.snapenhance.download.data

enum class MediaFilter(
    val key: String,
    val shouldIgnoreFilter: Boolean = false
) {
    NONE("none", true),
    PENDING("pending", true),
    CHAT_MEDIA("chat_media"),
    STORY("story"),
    SPOTLIGHT("spotlight"),
    PROFILE_PICTURE("profile_picture");

    fun matches(source: String?): Boolean {
        if (source == null) return false
        return source.contains(key, ignoreCase = true)
    }
}
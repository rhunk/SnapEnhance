package me.rhunk.snapenhance.ui.download

enum class MediaFilter(
    val mediaDisplayType: String? = null
) {
    NONE,
    PENDING,
    CHAT_MEDIA("Chat Media"),
    STORY("Story"),
    SPOTLIGHT("Spotlight");

    fun matches(source: String?): Boolean {
        if (mediaDisplayType == null) return true
        if (source == null) return false
        return source.contains(mediaDisplayType, ignoreCase = true)
    }
}
package me.rhunk.snapenhance.common.data.download

import me.rhunk.snapenhance.common.bridge.wrapper.LocaleWrapper

enum class MediaDownloadSource(
    val key: String,
    val pathName: String = key,
    val ignoreFilter: Boolean = false
) {
    NONE("none", ignoreFilter = true),
    PENDING("pending", ignoreFilter = true),
    CHAT_MEDIA("chat_media", "chat_media"),
    STORY("story",  "story"),
    PUBLIC_STORY("public_story", "public_story"),
    SPOTLIGHT("spotlight",  "spotlight"),
    PROFILE_PICTURE("profile_picture", "profile_picture"),
    STORY_LOGGER("story_logger", "story_logger"),
    MERGED("merged", "merged");

    fun matches(source: String?): Boolean {
        if (source == null) return false
        return source.contains(key, ignoreCase = true)
    }

    fun translate(translation: LocaleWrapper): String {
        return translation["media_download_source.$key"]
    }

    companion object {
        fun fromKey(key: String?): MediaDownloadSource {
            if (key == null) return NONE
            return entries.find { it.key == key } ?: NONE
        }
    }
}
package me.rhunk.snapenhance.core.features.impl.downloader.decoder

enum class AttachmentType(
    val key: String,
) {
    SNAP("snap"),
    STICKER("sticker"),
    EXTERNAL_MEDIA("external_media"),
    NOTE("note"),
    ORIGINAL_STORY("original_story"),
}
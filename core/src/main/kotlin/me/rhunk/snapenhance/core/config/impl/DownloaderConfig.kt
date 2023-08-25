package me.rhunk.snapenhance.core.config.impl

import me.rhunk.snapenhance.core.config.ConfigContainer
import me.rhunk.snapenhance.core.config.ConfigFlag
import me.rhunk.snapenhance.core.config.FeatureNotice

class DownloaderConfig : ConfigContainer() {
    val saveFolder = string("save_folder") { addFlags(ConfigFlag.FOLDER) }
    val autoDownloadSources = multiple("auto_download_sources",
        "friend_snaps",
        "friend_stories",
        "public_stories",
        "spotlight"
    )
    val preventSelfAutoDownload = boolean("prevent_self_auto_download")
    val pathFormat = multiple("path_format",
        "create_user_folder",
        "append_hash",
        "append_date_time",
        "append_type",
        "append_username"
    ).apply { set(mutableListOf("append_hash", "append_date_time", "append_type", "append_username")) }
    val allowDuplicate = boolean("allow_duplicate")
    val mergeOverlays = boolean("merge_overlays") { addNotices(FeatureNotice.MAY_CAUSE_CRASHES) }
    val forceImageFormat = unique("force_image_format", "jpg", "png", "webp") {
        addFlags(ConfigFlag.NO_TRANSLATE)
    }
    val chatDownloadContextMenu = boolean("chat_download_context_menu")
    val logging = multiple("logging", "started", "success", "progress", "failure").apply {
        set(mutableListOf("started", "success"))
    }
}
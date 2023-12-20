package me.rhunk.snapenhance.common.config.impl

import me.rhunk.snapenhance.common.config.ConfigContainer
import me.rhunk.snapenhance.common.config.ConfigFlag
import me.rhunk.snapenhance.common.config.FeatureNotice

class DownloaderConfig : ConfigContainer() {
    inner class FFMpegOptions : ConfigContainer() {
        val threads = integer("threads", 1)
        val preset = unique("preset", "ultrafast", "superfast", "veryfast", "faster", "fast", "medium", "slow", "slower", "veryslow") {
            addFlags(ConfigFlag.NO_TRANSLATE)
        }
        val constantRateFactor = integer("constant_rate_factor", 30)
        val videoBitrate = integer("video_bitrate", 5000)
        val audioBitrate = integer("audio_bitrate", 128)
        val customVideoCodec = string("custom_video_codec") { addFlags(ConfigFlag.NO_TRANSLATE) }
        val customAudioCodec = string("custom_audio_codec") { addFlags(ConfigFlag.NO_TRANSLATE) }
    }

    val saveFolder = string("save_folder") { addFlags(ConfigFlag.FOLDER); requireRestart() }
    val autoDownloadSources = multiple("auto_download_sources",
        "friend_snaps",
        "friend_stories",
        "public_stories",
        "spotlight"
    )
    val preventSelfAutoDownload = boolean("prevent_self_auto_download")
    val pathFormat = multiple("path_format",
        "create_author_folder",
        "create_source_folder",
        "append_hash",
        "append_source",
        "append_username",
        "append_date_time",
    ).apply { set(mutableListOf("append_hash", "append_date_time", "append_type", "append_username")) }
    val allowDuplicate = boolean("allow_duplicate")
    val mergeOverlays = boolean("merge_overlays") { addNotices(FeatureNotice.UNSTABLE) }
    val forceImageFormat = unique("force_image_format", "jpg", "png", "webp") {
        addFlags(ConfigFlag.NO_TRANSLATE)
    }
    val forceVoiceNoteFormat = unique("force_voice_note_format", "aac", "mp3", "opus") {
        addFlags(ConfigFlag.NO_TRANSLATE)
    }
    val downloadProfilePictures = boolean("download_profile_pictures") { requireRestart() }
    val operaDownloadButton = boolean("opera_download_button") { requireRestart() }
    val chatDownloadContextMenu = boolean("chat_download_context_menu")
    val ffmpegOptions = container("ffmpeg_options", FFMpegOptions()) { addNotices(FeatureNotice.UNSTABLE) }
    val logging = multiple("logging", "started", "success", "progress", "failure").apply {
        set(mutableListOf("success", "progress", "failure"))
    }
    val customPathFormat = string("custom_path_format") { addNotices(FeatureNotice.UNSTABLE) }
}
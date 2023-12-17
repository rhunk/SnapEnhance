package me.rhunk.snapenhance.common.data.download

import me.rhunk.snapenhance.common.config.impl.RootConfig
import java.text.SimpleDateFormat
import java.util.Locale


data class DashOptions(val offsetTime: Long, val duration: Long?)
data class InputMedia(
    val content: String,
    val type: DownloadMediaType,
    val encryption: MediaEncryptionKeyPair? = null,
    val attachmentType: String? = null,
    val isOverlay: Boolean = false,
)

class DownloadRequest(
    val inputMedias: Array<InputMedia>,
    val dashOptions: DashOptions? = null,
    private val flags: Int = 0,
) {
    object Flags {
        const val MERGE_OVERLAY = 1
        const val IS_DASH_PLAYLIST = 2
    }

    val isDashPlaylist: Boolean
        get() = flags and Flags.IS_DASH_PLAYLIST != 0

    val shouldMergeOverlay: Boolean
        get() = flags and Flags.MERGE_OVERLAY != 0
}

fun String.sanitizeForPath(): String {
    return this.replace(" ", "_")
        .replace(Regex("\\p{Cntrl}"), "")
}

fun createNewFilePath(
    config: RootConfig,
    hexHash: String,
    downloadSource: MediaDownloadSource,
    mediaAuthor: String,
    creationTimestamp: Long?
): String {
    val pathFormat by config.downloader.pathFormat
    val sanitizedMediaAuthor = mediaAuthor.sanitizeForPath().ifEmpty { hexHash }

    val currentDateTime = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.ENGLISH).format(creationTimestamp ?: System.currentTimeMillis())

    val finalPath = StringBuilder()

    fun appendFileName(string: String) {
        if (finalPath.isEmpty() || finalPath.endsWith("/")) {
            finalPath.append(string)
        } else {
            finalPath.append("_").append(string)
        }
    }

    if (pathFormat.contains("create_author_folder")) {
        finalPath.append(sanitizedMediaAuthor).append("/")
    }
    if (pathFormat.contains("create_source_folder")) {
        finalPath.append(downloadSource.pathName).append("/")
    }
    if (pathFormat.contains("append_hash")) {
        appendFileName(hexHash)
    }
    if (pathFormat.contains("append_source")) {
        appendFileName(downloadSource.pathName)
    }
    if (pathFormat.contains("append_username")) {
        appendFileName(sanitizedMediaAuthor)
    }
    if (pathFormat.contains("append_date_time")) {
        appendFileName(currentDateTime)
    }

    if (finalPath.isEmpty()) finalPath.append(hexHash)

    return finalPath.toString()
}
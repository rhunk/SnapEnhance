package me.rhunk.snapenhance.download.data


data class DashOptions(val offsetTime: Long, val duration: Long?)
data class InputMedia(
    val content: String,
    val type: DownloadMediaType,
    val encryption: MediaEncryptionKeyPair? = null
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
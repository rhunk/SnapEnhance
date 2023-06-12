package me.rhunk.snapenhance.download

import android.content.Intent
import android.os.Bundle
import me.rhunk.snapenhance.BuildConfig
import me.rhunk.snapenhance.ModContext

class ClientDownloadManager (
    private val context: ModContext,
    private val outputPath: String,
    private val mediaDisplaySource: String?,
    private val mediaDisplayType: String?,
    private val iconUrl: String?
) {
    private fun sendToBroadcastReceiver(bundle: Bundle) {
        val intent = Intent()
        intent.setClassName(BuildConfig.APPLICATION_ID, MediaDownloadReceiver::class.java.name)
        intent.action = MediaDownloadReceiver.DOWNLOAD_ACTION
        intent.putExtras(bundle)
        context.androidContext.sendBroadcast(intent)
    }

    private fun sendToBroadcastReceiver(
        outputPath: String,
        inputMedias: Array<String>,
        inputTypes: Array<DownloadMediaType>,
        mediaEncryption: Map<String, Pair<String, String>>,
        shouldMergeOverlay: Boolean = false,
        isDashPlaylist: Boolean = false,
        extras: Bundle.() -> Unit = {}
    ) {
        val bundle = Bundle()
        bundle.putString("outputPath", outputPath)
        bundle.putStringArray("inputMedias", inputMedias)
        bundle.putStringArray("inputTypes", inputTypes.map { it.name }.toTypedArray())
        bundle.putStringArray("mediaEncryption", mediaEncryption.map { "${it.key}|${it.value.first}|${it.value.second}" }.toTypedArray())
        bundle.putBoolean("shouldMergeOverlay", shouldMergeOverlay)
        bundle.putBoolean("isDashPlaylist", isDashPlaylist)
        bundle.putString("mediaDisplaySource", mediaDisplaySource)
        bundle.putString("mediaDisplayType", mediaDisplayType)
        bundle.putString("iconUrl", iconUrl)
        bundle.extras()
        sendToBroadcastReceiver(bundle)
    }

    fun downloadDashMedia(playlistUrl: String, offsetTime: Long, duration: Long) {
        sendToBroadcastReceiver(
            outputPath,
            arrayOf(playlistUrl),
            arrayOf(DownloadMediaType.REMOTE_MEDIA),
            mapOf(),
            isDashPlaylist = true
        ) {
            putBundle("dashOptions", Bundle().apply {
                putLong("offsetTime", offsetTime)
                putLong("duration", duration)
            })
        }
    }

    fun downloadMedia(mediaData: String, mediaType: DownloadMediaType, encryption: Pair<String, String>? = null) {
        sendToBroadcastReceiver(
            outputPath,
            arrayOf(mediaData),
            arrayOf(mediaType),
            if (encryption != null) mapOf(mediaData to encryption) else mapOf()
        )
    }

    fun downloadMediaWithOverlay(
        videoData: String,
        overlayData: String,
        videoType: DownloadMediaType = DownloadMediaType.LOCAL_MEDIA,
        overlayType: DownloadMediaType = DownloadMediaType.LOCAL_MEDIA,
        videoEncryption: Pair<String, String>? = null,
        overlayEncryption: Pair<String, String>? = null)
    {

        val encryptionMap = mutableMapOf<String, Pair<String, String>>()

        if (videoEncryption != null) encryptionMap[videoData] = videoEncryption
        if (overlayEncryption != null) encryptionMap[overlayData] = overlayEncryption

        sendToBroadcastReceiver(
            outputPath,
            arrayOf(videoData, overlayData),
            arrayOf(videoType, overlayType),
            encryptionMap,
            shouldMergeOverlay = true
        )
    }
}
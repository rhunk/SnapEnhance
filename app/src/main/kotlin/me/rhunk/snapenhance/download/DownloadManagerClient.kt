package me.rhunk.snapenhance.download

import android.content.Intent
import android.os.Bundle
import me.rhunk.snapenhance.BuildConfig
import me.rhunk.snapenhance.ModContext
import me.rhunk.snapenhance.download.data.DownloadRequest
import me.rhunk.snapenhance.download.data.MediaEncryptionKeyPair
import me.rhunk.snapenhance.download.enums.DownloadMediaType

class DownloadManagerClient (
    private val context: ModContext,
    private val outputPath: String,
    private val mediaDisplaySource: String?,
    private val mediaDisplayType: String?,
    private val iconUrl: String?
) {
    private fun sendToBroadcastReceiver(bundle: Bundle) {
        val intent = Intent()
        intent.setClassName(BuildConfig.APPLICATION_ID, DownloadManagerReceiver::class.java.name)
        intent.action = DownloadManagerReceiver.DOWNLOAD_ACTION
        intent.putExtras(bundle)
        context.androidContext.sendBroadcast(intent)
    }

    private fun sendToBroadcastReceiver(
        request: DownloadRequest,
        extras: Bundle.() -> Unit = {}
    ) {
        sendToBroadcastReceiver(request.toBundle().apply {
            putString("outputPath", outputPath)
            putString("mediaDisplaySource", mediaDisplaySource)
            putString("mediaDisplayType", mediaDisplayType)
            putString("iconUrl", iconUrl)
        }.apply(extras))
    }

    fun downloadDashMedia(playlistUrl: String, offsetTime: Long, duration: Long) {
        sendToBroadcastReceiver(
            DownloadRequest(
                inputMedias = arrayOf(playlistUrl),
                inputTypes = arrayOf(DownloadMediaType.REMOTE_MEDIA.name),
                flags = DownloadRequest.Flags.IS_DASH_PLAYLIST
            )
        ) {
            putBundle("dashOptions", Bundle().apply {
                putLong("offsetTime", offsetTime)
                putLong("duration", duration)
            })
        }
    }

    fun downloadMedia(mediaData: String, mediaType: DownloadMediaType, encryption: MediaEncryptionKeyPair? = null) {
        sendToBroadcastReceiver(
            DownloadRequest(
                inputMedias = arrayOf(mediaData),
                inputTypes = arrayOf(mediaType.name),
                mediaEncryption = if (encryption != null) mapOf(mediaData to encryption) else mapOf()
            )
        )
    }

    fun downloadMediaWithOverlay(
        videoData: String,
        overlayData: String,
        videoType: DownloadMediaType = DownloadMediaType.LOCAL_MEDIA,
        overlayType: DownloadMediaType = DownloadMediaType.LOCAL_MEDIA,
        videoEncryption: MediaEncryptionKeyPair? = null,
        overlayEncryption: MediaEncryptionKeyPair? = null)
    {
        val encryptionMap = mutableMapOf<String, MediaEncryptionKeyPair>()

        if (videoEncryption != null) encryptionMap[videoData] = videoEncryption
        if (overlayEncryption != null) encryptionMap[overlayData] = overlayEncryption
        sendToBroadcastReceiver(DownloadRequest(
            inputMedias = arrayOf(videoData, overlayData),
            inputTypes = arrayOf(videoType.name, overlayType.name),
            mediaEncryption = encryptionMap,
            flags = DownloadRequest.Flags.SHOULD_MERGE_OVERLAY
        ))
    }
}
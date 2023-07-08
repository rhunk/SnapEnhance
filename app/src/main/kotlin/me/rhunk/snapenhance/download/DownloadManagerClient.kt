package me.rhunk.snapenhance.download

import android.content.Intent
import android.os.Bundle
import me.rhunk.snapenhance.BuildConfig
import me.rhunk.snapenhance.ModContext
import me.rhunk.snapenhance.download.data.DashOptions
import me.rhunk.snapenhance.download.data.DownloadMetadata
import me.rhunk.snapenhance.download.data.DownloadRequest
import me.rhunk.snapenhance.download.data.InputMedia
import me.rhunk.snapenhance.download.data.MediaEncryptionKeyPair
import me.rhunk.snapenhance.download.enums.DownloadMediaType

class DownloadManagerClient (
    private val context: ModContext,
    private val metadata: DownloadMetadata
) {
    private fun sendToBroadcastReceiver(request: DownloadRequest) {
        val intent = Intent()
        intent.setClassName(BuildConfig.APPLICATION_ID, DownloadManagerReceiver::class.java.name)
        intent.action = DownloadManagerReceiver.DOWNLOAD_ACTION
        intent.putExtras(Bundle().apply {
            putString(DownloadManagerReceiver.DOWNLOAD_REQUEST_EXTRA, context.gson.toJson(request))
            putString(DownloadManagerReceiver.DOWNLOAD_METADATA_EXTRA, context.gson.toJson(metadata))
        })
        context.androidContext.sendBroadcast(intent)
    }

    fun downloadDashMedia(playlistUrl: String, offsetTime: Long, duration: Long?) {
        sendToBroadcastReceiver(
            DownloadRequest(
                inputMedias = arrayOf(InputMedia(
                    content = playlistUrl,
                    type = DownloadMediaType.REMOTE_MEDIA
                )),
                dashOptions = DashOptions(offsetTime, duration),
                flags = DownloadRequest.Flags.IS_DASH_PLAYLIST
            )
        )
    }

    fun downloadSingleMedia(mediaData: String, mediaType: DownloadMediaType, encryption: MediaEncryptionKeyPair? = null) {
        sendToBroadcastReceiver(
            DownloadRequest(
                inputMedias = arrayOf(InputMedia(
                    content = mediaData,
                    type = mediaType,
                    encryption = encryption
                ))
            )
        )
    }

    fun downloadMediaWithOverlay(
        original: InputMedia,
        overlay: InputMedia,
    ) {
        sendToBroadcastReceiver(
            DownloadRequest(
                inputMedias = arrayOf(original, overlay),
                flags = DownloadRequest.Flags.MERGE_OVERLAY
            )
        )
    }
}
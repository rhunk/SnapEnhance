package me.rhunk.snapenhance.download.data

import android.os.Bundle
import kotlinx.coroutines.Job
import me.rhunk.snapenhance.download.MediaDownloadReceiver
import me.rhunk.snapenhance.download.enums.DownloadStage

data class PendingDownload(
    var outputFile: String? = null,
    var job: Job? = null,

    var id: Int = 0,
    val outputPath: String,
    val mediaDisplayType: String?,
    val mediaDisplaySource: String?,
    val iconUrl: String?
) {
    companion object {
        fun fromBundle(bundle: Bundle): PendingDownload {
            return PendingDownload(
                outputPath = bundle.getString("outputPath")!!,
                mediaDisplayType = bundle.getString("mediaDisplayType"),
                mediaDisplaySource = bundle.getString("mediaDisplaySource"),
                iconUrl = bundle.getString("iconUrl")
            )
        }
    }

    var changeListener = { _: DownloadStage, _: DownloadStage -> }
    private var _stage: DownloadStage = DownloadStage.PENDING
    var downloadStage: DownloadStage
        get() = synchronized(this) {
            _stage
        }
        set(value) = synchronized(this) {
            changeListener(_stage, value)
            _stage = value
            MediaDownloadReceiver.downloadTaskManager.updateTask(this)
        }

    fun isJobActive(): Boolean {
        return job?.isActive ?: false
    }

    fun cancel() {
        job?.cancel()
        downloadStage = DownloadStage.CANCELLED
    }
}

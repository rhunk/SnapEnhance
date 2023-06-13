package me.rhunk.snapenhance.download

import android.content.Intent
import kotlinx.coroutines.Job

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
        fun fromIntent(intent: Intent): PendingDownload {
            return PendingDownload(
                outputPath = intent.getStringExtra("outputPath")!!,
                mediaDisplayType = intent.getStringExtra("mediaDisplayType"),
                mediaDisplaySource = intent.getStringExtra("mediaDisplaySource"),
                iconUrl = intent.getStringExtra("iconUrl")
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

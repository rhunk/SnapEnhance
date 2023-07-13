package me.rhunk.snapenhance.download.data

import kotlinx.coroutines.Job
import me.rhunk.snapenhance.SharedContext
import me.rhunk.snapenhance.download.enums.DownloadStage

data class PendingDownload(
    var downloadId: Int = 0,
    var outputFile: String? = null,
    var job: Job? = null,

    val metadata : DownloadMetadata
) {
    var changeListener = { _: DownloadStage, _: DownloadStage -> }
    private var _stage: DownloadStage = DownloadStage.PENDING
    var downloadStage: DownloadStage
        get() = synchronized(this) {
            _stage
        }
        set(value) = synchronized(this) {
            changeListener(_stage, value)
            _stage = value
            SharedContext.downloadTaskManager.updateTask(this)
        }

    fun isJobActive(): Boolean {
        return job?.isActive ?: false
    }

    fun cancel() {
        job?.cancel()
        downloadStage = DownloadStage.CANCELLED
    }
}

package me.rhunk.snapenhance.download.data

import kotlinx.coroutines.Job
import me.rhunk.snapenhance.download.DownloadTaskManager

data class DownloadObject(
    var downloadId: Int = 0,
    var outputFile: String? = null,
    val metadata : DownloadMetadata
) {
    lateinit var downloadTaskManager: DownloadTaskManager
    var job: Job? = null

    var changeListener = { _: DownloadStage, _: DownloadStage -> }
    private var _stage: DownloadStage = DownloadStage.PENDING
    var downloadStage: DownloadStage
        get() = synchronized(this) {
            _stage
        }
        set(value) = synchronized(this) {
            changeListener(_stage, value)
            _stage = value
            downloadTaskManager.updateTask(this)
        }

    fun isJobActive() = job?.isActive == true

    fun cancel() {
        downloadStage = DownloadStage.CANCELLED
        job?.cancel()
    }
}

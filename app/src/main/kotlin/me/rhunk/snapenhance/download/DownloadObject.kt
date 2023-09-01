package me.rhunk.snapenhance.download

import kotlinx.coroutines.Job
import me.rhunk.snapenhance.core.download.data.DownloadMetadata
import me.rhunk.snapenhance.core.download.data.DownloadStage

data class DownloadObject(
    var downloadId: Int = 0,
    var outputFile: String? = null,
    val metadata : DownloadMetadata
) {
    var job: Job? = null

    var changeListener = { _: DownloadStage, _: DownloadStage -> }
    lateinit var updateTaskCallback: (DownloadObject) -> Unit

    private var _stage: DownloadStage = DownloadStage.PENDING
    var downloadStage: DownloadStage
        get() = synchronized(this) {
            _stage
        }
        set(value) = synchronized(this) {
            changeListener(_stage, value)
            _stage = value
            updateTaskCallback(this)
        }

    fun isJobActive() = job?.isActive == true

    fun cancel() {
        downloadStage = DownloadStage.CANCELLED
        job?.cancel()
    }
}

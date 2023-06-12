package me.rhunk.snapenhance.download

import kotlinx.coroutines.Job

data class PendingDownload(
    val outputPath: String,
    var outputFile: String? = null,
    var job: Job? = null,
) {
    private var _state: DownloadStage = DownloadStage.PENDING

    var changeListener = { _: DownloadStage, _: DownloadStage -> }
    var downloadStage: DownloadStage
        get() = synchronized(this) {
            _state
        }
        set(value) = synchronized(this) {
            changeListener(_state, value)
            _state = value
        }

    fun isJobActive(): Boolean {
        return job?.isActive ?: false
    }
    fun cancel() {
        job?.cancel()
        downloadStage = DownloadStage.CANCELLED
    }
}

package me.rhunk.snapenhance.download

import android.content.Intent
import kotlinx.coroutines.Job

data class PendingDownload(
    private val intent: Intent,
    var outputFile: String? = null,
    var job: Job? = null
) {
    private var _state: DownloadStage = DownloadStage.PENDING

    var changeListener = { _: DownloadStage, _: DownloadStage -> }
    val outputPath: String get() = intent.getStringExtra("outputPath")!!
    val mediaDisplayType: String? get() = intent.getStringExtra("mediaDisplayType")
    val mediaDisplaySource: String? get() = intent.getStringExtra("mediaDisplaySource")
    val iconUrl: String? get() = intent.getStringExtra("iconUrl")

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

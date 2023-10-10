package me.rhunk.snapenhance.common.data.download

enum class DownloadStage(
    val isFinalStage: Boolean = false,
) {
    PENDING(false),
    DOWNLOADING(false),
    MERGING(false),
    DOWNLOADED(true),
    SAVED(true),
    MERGE_FAILED(true),
    FAILED(true),
    CANCELLED(true)
}
package me.rhunk.snapenhance.download.data

data class DownloadMetadata(
    val mediaIdentifier: String?,
    val outputPath: String,
    val mediaDisplaySource: String?,
    val mediaDisplayType: String?,
    val iconUrl: String?
)
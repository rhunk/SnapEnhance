package me.rhunk.snapenhance.common.data.download

data class DownloadMetadata(
    val mediaIdentifier: String,
    val outputPath: String,
    val mediaAuthor: String?,
    val downloadSource: String,
    val iconUrl: String?
)
package me.rhunk.snapenhance.core.download.data

data class DownloadMetadata(
    val mediaIdentifier: String?,
    val outputPath: String,
    val mediaAuthor: String?,
    val downloadSource: String,
    val iconUrl: String?
)
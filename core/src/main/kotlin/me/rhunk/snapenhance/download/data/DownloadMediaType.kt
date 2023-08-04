package me.rhunk.snapenhance.download.data

import android.net.Uri

enum class DownloadMediaType {
    PROTO_MEDIA,
    DIRECT_MEDIA,
    REMOTE_MEDIA,
    LOCAL_MEDIA;

    companion object {
        fun fromUri(uri: Uri): DownloadMediaType {
            return when (uri.scheme) {
                "proto" -> PROTO_MEDIA
                "direct" -> DIRECT_MEDIA
                "http", "https" -> REMOTE_MEDIA
                "file" -> LOCAL_MEDIA
                else -> throw IllegalArgumentException("Unknown uri scheme: ${uri.scheme}")
            }
        }
    }
}
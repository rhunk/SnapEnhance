package me.rhunk.snapenhance.core.util.snap

import me.rhunk.snapenhance.Constants
import me.rhunk.snapenhance.core.download.data.SplitMediaAssetType
import me.rhunk.snapenhance.core.util.download.RemoteMediaResolver
import me.rhunk.snapenhance.core.util.protobuf.ProtoReader
import me.rhunk.snapenhance.data.ContentType
import me.rhunk.snapenhance.data.FileType
import java.io.ByteArrayInputStream
import java.io.FileNotFoundException
import java.io.InputStream
import java.util.zip.ZipInputStream


object MediaDownloaderHelper {
    fun getMessageMediaEncryptionInfo(protoReader: ProtoReader, contentType: ContentType, isArroyo: Boolean): ProtoReader? {
        val messageContainerPath = if (isArroyo) protoReader.followPath(*Constants.ARROYO_MEDIA_CONTAINER_PROTO_PATH)!! else protoReader
        val mediaContainerPath = if (contentType == ContentType.NOTE) intArrayOf(6, 1, 1) else intArrayOf(5, 1, 1)

        return when (contentType) {
            ContentType.NOTE -> messageContainerPath.followPath(*mediaContainerPath)
            ContentType.SNAP -> messageContainerPath.followPath(*(intArrayOf(11) + mediaContainerPath))
            ContentType.EXTERNAL_MEDIA -> {
                val externalMediaTypes = arrayOf(
                    intArrayOf(3, 3, *mediaContainerPath), //normal external media
                    intArrayOf(7, 15, 1, 1), //attached audio note
                    intArrayOf(7, 12, 3, *mediaContainerPath), //attached story reply
                    intArrayOf(7, 3, *mediaContainerPath), //original story reply
                )
                externalMediaTypes.forEach { path ->
                    messageContainerPath.followPath(*path)?.also { return it }
                }
                null
            }
            else -> null
        }
    }

    fun downloadMediaFromReference(
        mediaReference: ByteArray,
        decryptionCallback: (InputStream) -> InputStream,
    ): Map<SplitMediaAssetType, ByteArray> {
        val inputStream = RemoteMediaResolver.downloadBoltMedia(mediaReference) ?: throw FileNotFoundException("Unable to get media key. Check the logs for more info")
        val content = decryptionCallback(inputStream).readBytes()
        val fileType = FileType.fromByteArray(content)
        val isZipFile = fileType == FileType.ZIP

        //videos with overlay are packed in a zip file
        //there are 2 files in the zip file, the video (webm) and the overlay (png)
        if (isZipFile) {
            var videoData: ByteArray? = null
            var overlayData: ByteArray? = null
            val zipInputStream = ZipInputStream(ByteArrayInputStream(content))
            while (zipInputStream.nextEntry != null) {
                val zipEntryData: ByteArray = zipInputStream.readBytes()
                val entryFileType = FileType.fromByteArray(zipEntryData)
                if (entryFileType.isVideo) {
                    videoData = zipEntryData
                } else if (entryFileType.isImage) {
                    overlayData = zipEntryData
                }
            }
            videoData ?: throw FileNotFoundException("Unable to find video file in zip file")
            overlayData ?: throw FileNotFoundException("Unable to find overlay file in zip file")
            return mapOf(SplitMediaAssetType.ORIGINAL to videoData, SplitMediaAssetType.OVERLAY to overlayData)
        }

        return mapOf(SplitMediaAssetType.ORIGINAL to content)
    }
}
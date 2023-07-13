package me.rhunk.snapenhance.util.snap

import com.arthenica.ffmpegkit.FFmpegKit
import com.arthenica.ffmpegkit.FFmpegSession
import kotlinx.coroutines.suspendCancellableCoroutine
import me.rhunk.snapenhance.Constants
import me.rhunk.snapenhance.data.ContentType
import me.rhunk.snapenhance.data.FileType
import me.rhunk.snapenhance.download.data.SplitMediaAssetType
import me.rhunk.snapenhance.util.download.RemoteMediaResolver
import me.rhunk.snapenhance.util.protobuf.ProtoReader
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileNotFoundException
import java.io.InputStream
import java.util.concurrent.Executors
import java.util.zip.ZipInputStream


object MediaDownloaderHelper {
    fun getMessageMediaInfo(protoReader: ProtoReader, contentType: ContentType, isArroyo: Boolean): ProtoReader? {
        val messageContainerPath = if (isArroyo) protoReader.readPath(*Constants.ARROYO_MEDIA_CONTAINER_PROTO_PATH)!! else protoReader
        val mediaContainerPath = if (contentType == ContentType.NOTE) intArrayOf(6, 1, 1) else intArrayOf(5, 1, 1)

        return when (contentType) {
            ContentType.NOTE -> messageContainerPath.readPath(*mediaContainerPath)
            ContentType.SNAP -> messageContainerPath.readPath(*(intArrayOf(11) + mediaContainerPath))
            ContentType.EXTERNAL_MEDIA -> messageContainerPath.readPath(*(intArrayOf(3, 3) + mediaContainerPath))
            else -> null
        }
    }

    fun downloadMediaFromReference(mediaReference: ByteArray, decryptionCallback: (InputStream) -> InputStream): Map<SplitMediaAssetType, ByteArray> {
        val inputStream: InputStream = RemoteMediaResolver.downloadBoltMedia(mediaReference) ?: throw FileNotFoundException("Unable to get media key. Check the logs for more info")
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


    private suspend fun runFFmpegAsync(vararg args: String) = suspendCancellableCoroutine<FFmpegSession> {
        FFmpegKit.executeAsync(args.joinToString(" "), { session ->
            it.resumeWith(
                if (session.returnCode.isValueSuccess) {
                    Result.success(session)
                } else {
                    Result.failure(Exception(session.output))
                }
            )
        },
            Executors.newSingleThreadExecutor())
    }

    //TODO: implement setting parameters

    suspend fun downloadDashChapterFile(
        dashPlaylist: File,
        output: File,
        startTime: Long,
        duration: Long?) {
        runFFmpegAsync(
            "-y", "-i", dashPlaylist.absolutePath, "-ss", "'${startTime}ms'", *(if (duration != null) arrayOf("-t", "'${duration}ms'") else arrayOf()),
            "-c:v", "libx264", "-preset", "ultrafast", "-threads", "6", "-q:v", "13", output.absolutePath
        )
    }

    suspend fun mergeOverlayFile(
        media: File,
        overlay: File,
        output: File
    ) {
        runFFmpegAsync(
            "-y", "-i", media.absolutePath, "-i", overlay.absolutePath,
            "-filter_complex", "\"[0]scale2ref[img][vid];[img]setsar=1[img];[vid]nullsink;[img][1]overlay=(W-w)/2:(H-h)/2,scale=2*trunc(iw*sar/2):2*trunc(ih/2)\"",
            "-c:v", "libx264", "-b:v", "5M", "-c:a", "copy", "-preset", "ultrafast", "-threads", "6", output.absolutePath
        )
    }
}
package me.rhunk.snapenhance.util

import com.arthenica.ffmpegkit.FFmpegKit
import me.rhunk.snapenhance.Logger
import me.rhunk.snapenhance.data.FileType
import me.rhunk.snapenhance.util.download.RemoteMediaResolver
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileNotFoundException
import java.io.FileOutputStream
import java.io.InputStream
import java.util.zip.ZipInputStream

enum class MediaType {
    ORIGINAL, OVERLAY
}

object MediaDownloaderHelper {
    fun downloadMediaFromReference(mediaReference: ByteArray, mergeOverlay: Boolean, isPreviewMode: Boolean, decryptionCallback: (InputStream) -> InputStream): Map<MediaType, ByteArray> {
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
            if (mergeOverlay) {
                val mergedVideo = mergeOverlay(videoData, overlayData, isPreviewMode)
                return mapOf(MediaType.ORIGINAL to mergedVideo)
            }
            return mapOf(MediaType.ORIGINAL to videoData, MediaType.OVERLAY to overlayData)
        }

        return mapOf(MediaType.ORIGINAL to content)
    }

    fun downloadDashChapterFile(dashPlaylist: File, startTime: Long, duration: Long?): File {
        val outputFile = File.createTempFile("output", ".mp4")

        val ffmpegSession = FFmpegKit.execute(
            "-y -i " +
                    dashPlaylist.absolutePath +
                    " -ss '${startTime}ms'" +
                    (if (duration != null) " -t '${duration}ms'" else "") +
                    " -c:v libx264 -threads 6 -q:v 13 " + outputFile.absolutePath
        )

        if (!ffmpegSession.returnCode.isValueSuccess) {
            throw Exception(ffmpegSession.output)
        }
        return outputFile
    }

    fun mergeOverlayFile(media: File, overlay: File, outputFileType: FileType, isThumbnail: Boolean = false): File {
        val mergedFile = File.createTempFile("merged", "." + outputFileType.fileExtension)

        val ffmpegSession = FFmpegKit.execute(
            "-y -i " +
                    media.absolutePath +
                    " -i " +
                    overlay.absolutePath +
                    " -filter_complex \"[0]scale2ref[img][vid];[img]setsar=1[img];[vid]nullsink; [img][1]overlay=(W-w)/2:(H-h)/2,scale=2*trunc(iw*sar/2):2*trunc(ih/2)\" -c:v libx264 -q:v 13 -c:a copy " +
                    "  -threads 6 ${(if (isThumbnail) "-frames:v 1" else "")} " +
                    mergedFile.absolutePath
        )

        if (ffmpegSession.returnCode.value != 0) {
            mergedFile.delete()
            Logger.error(ffmpegSession.output)
            throw IllegalStateException("Failed to merge video and overlay. See logs for more details.")
        }
        return mergedFile
    }

    fun mergeOverlay(original: ByteArray, overlay: ByteArray, isPreviewMode: Boolean): ByteArray {
        val originalFileType = FileType.fromByteArray(original)
        val overlayFileType = FileType.fromByteArray(overlay)
        //merge files
        val mergedFile = File.createTempFile("merged", "." + originalFileType.fileExtension)
        val tempVideoFile = File.createTempFile("original", "." + originalFileType.fileExtension).also {
            with(FileOutputStream(it)) {
                write(original)
                close()
            }
        }
        val tempOverlayFile = File.createTempFile("overlay", "." + overlayFileType.fileExtension).also {
            with(FileOutputStream(it)) {
                write(overlay)
                close()
            }
        }

        //TODO: improve ffmpeg speed
        val fFmpegSession = FFmpegKit.execute(
            "-y -i " +
                    tempVideoFile.absolutePath +
                    " -i " +
                    tempOverlayFile.absolutePath +
                    " -filter_complex \"[0]scale2ref[img][vid];[img]setsar=1[img];[vid]nullsink; [img][1]overlay=(W-w)/2:(H-h)/2,scale=2*trunc(iw*sar/2):2*trunc(ih/2)\" -c:v libx264 -q:v 13 -c:a copy " +
                    "  -threads 6 ${(if (isPreviewMode) "-frames:v 1" else "")} " +
                    mergedFile.absolutePath
        )
        tempVideoFile.delete()
        tempOverlayFile.delete()
        if (fFmpegSession.returnCode.value != 0) {
            mergedFile.delete()
            Logger.xposedLog(fFmpegSession.output)
            throw IllegalStateException("Failed to merge video and overlay. See logs for more details.")
        }
        val mergedFileData: ByteArray = FileInputStream(mergedFile).readBytes()
        mergedFile.delete()
        return mergedFileData
    }
}
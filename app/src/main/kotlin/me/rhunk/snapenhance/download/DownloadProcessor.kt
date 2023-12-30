package me.rhunk.snapenhance.download

import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.widget.Toast
import androidx.documentfile.provider.DocumentFile
import com.google.gson.GsonBuilder
import kotlinx.coroutines.Job
import kotlinx.coroutines.job
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import me.rhunk.snapenhance.RemoteSideContext
import me.rhunk.snapenhance.bridge.DownloadCallback
import me.rhunk.snapenhance.common.Constants
import me.rhunk.snapenhance.common.ReceiversConfig
import me.rhunk.snapenhance.common.data.FileType
import me.rhunk.snapenhance.common.data.download.DownloadMediaType
import me.rhunk.snapenhance.common.data.download.DownloadMetadata
import me.rhunk.snapenhance.common.data.download.DownloadRequest
import me.rhunk.snapenhance.common.data.download.InputMedia
import me.rhunk.snapenhance.common.data.download.SplitMediaAssetType
import me.rhunk.snapenhance.common.util.snap.MediaDownloaderHelper
import me.rhunk.snapenhance.common.util.snap.RemoteMediaResolver
import me.rhunk.snapenhance.task.PendingTask
import me.rhunk.snapenhance.task.PendingTaskListener
import me.rhunk.snapenhance.task.Task
import me.rhunk.snapenhance.task.TaskStatus
import me.rhunk.snapenhance.task.TaskType
import java.io.File
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.ConcurrentHashMap
import javax.xml.parsers.DocumentBuilderFactory
import javax.xml.transform.TransformerFactory
import javax.xml.transform.dom.DOMSource
import javax.xml.transform.stream.StreamResult
import kotlin.coroutines.coroutineContext
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

data class DownloadedFile(
    val file: File,
    val fileType: FileType
)

/**
 * DownloadProcessor handles the download requests of the user
 */
@OptIn(ExperimentalEncodingApi::class)
class DownloadProcessor (
    private val remoteSideContext: RemoteSideContext,
    private val callback: DownloadCallback
) {

    private val translation by lazy {
        remoteSideContext.translation.getCategory("download_processor")
    }

    private val gson by lazy { GsonBuilder().setPrettyPrinting().create() }

    private fun fallbackToast(message: Any) {
        android.os.Handler(remoteSideContext.androidContext.mainLooper).post {
            Toast.makeText(remoteSideContext.androidContext, message.toString(), Toast.LENGTH_SHORT).show()
        }
    }

    private fun callbackOnSuccess(path: String) = runCatching {
        callback.onSuccess(path)
    }.onFailure {
        fallbackToast(it)
    }

    private fun callbackOnFailure(message: String, throwable: String? = null) = runCatching {
        callback.onFailure(message, throwable)
    }.onFailure {
        fallbackToast("$message\n$throwable")
    }

    private fun callbackOnProgress(message: String) = runCatching {
        callback.onProgress(message)
    }.onFailure {
        fallbackToast(it)
    }

    private fun newFFMpegProcessor(pendingTask: PendingTask) = FFMpegProcessor.newFFMpegProcessor(remoteSideContext, pendingTask)

    @SuppressLint("UnspecifiedRegisterReceiverFlag")
    suspend fun saveMediaToGallery(pendingTask: PendingTask, inputFile: File, metadata: DownloadMetadata) {
        if (coroutineContext.job.isCancelled) return

        runCatching {
            var fileType = FileType.fromFile(inputFile)

            if (fileType.isImage) {
                remoteSideContext.config.root.downloader.forceImageFormat.getNullable()?.let { format ->
                    val bitmap = BitmapFactory.decodeFile(inputFile.absolutePath) ?: throw Exception("Failed to decode bitmap")
                    @Suppress("DEPRECATION") val compressFormat = when (format) {
                        "png" -> Bitmap.CompressFormat.PNG
                        "jpg" -> Bitmap.CompressFormat.JPEG
                        "webp" -> Bitmap.CompressFormat.WEBP
                        else -> throw Exception("Invalid image format")
                    }

                    pendingTask.updateProgress("Converting image to $format")
                    inputFile.outputStream().use {
                        bitmap.compress(compressFormat, 100, it)
                    }
                    fileType = FileType.fromFile(inputFile)
                }
            }

            val fileName = metadata.outputPath.substringAfterLast("/") + "." + fileType.fileExtension

            val outputFolder = DocumentFile.fromTreeUri(remoteSideContext.androidContext, Uri.parse(remoteSideContext.config.root.downloader.saveFolder.get()))
                ?: throw Exception("Failed to open output folder")

            val outputFileFolder = metadata.outputPath.let {
                if (it.contains("/")) {
                    it.substringBeforeLast("/").split("/").fold(outputFolder) { folder, name ->
                        folder.findFile(name) ?: folder.createDirectory(name)!!
                    }
                } else {
                    outputFolder
                }
            }

            val outputFile = outputFileFolder.createFile(fileType.mimeType, fileName)!!

            pendingTask.updateProgress("Saving media to gallery")
            remoteSideContext.androidContext.contentResolver.openOutputStream(outputFile.uri)!!.use { outputStream ->
                inputFile.inputStream().use { inputStream ->
                    inputStream.copyTo(outputStream)
                }
            }

            pendingTask.task.extra = outputFile.uri.toString()
            pendingTask.success()

            runCatching {
                remoteSideContext.androidContext.sendBroadcast(Intent("android.intent.action.MEDIA_SCANNER_SCAN_FILE").apply {
                    data = outputFile.uri
                })
            }.onFailure {
                remoteSideContext.log.error("Failed to scan media file", it)
                callbackOnFailure(translation.format("failed_gallery_toast", "error" to it.toString()), it.message)
            }

            remoteSideContext.log.verbose("download complete")
            callbackOnSuccess(fileName)
        }.onFailure { exception ->
            remoteSideContext.log.error("Failed to save media to gallery", exception)
            callbackOnFailure(translation.format("failed_gallery_toast", "error" to exception.toString()), exception.message)
            pendingTask.fail("Failed to save media to gallery")
        }
    }

    private fun createMediaTempFile(): File {
        return File.createTempFile("media", ".tmp")
    }

    private fun downloadInputMedias(pendingTask: PendingTask, downloadRequest: DownloadRequest) = runBlocking {
        val jobs = mutableListOf<Job>()
        val downloadedMedias = mutableMapOf<InputMedia, File>()
        var totalSize = 1L
        val inputMediaDownloadedBytes = mutableMapOf<InputMedia, Long>()
        val inputMediaProgress = ConcurrentHashMap<InputMedia, String>()

        fun updateDownloadProgress() {
            pendingTask.updateProgress(
                inputMediaProgress.values.joinToString("\n"),
                progress = (inputMediaDownloadedBytes.values.sum() * 100 / totalSize).toInt().coerceIn(0, 100)
            )
        }

        downloadRequest.inputMedias.forEach { inputMedia ->
            fun setProgress(progress: String) {
                inputMediaProgress[inputMedia] = progress
                updateDownloadProgress()
            }

            fun handleInputStream(inputStream: InputStream, estimatedSize: Long = 0L) {
                createMediaTempFile().apply {
                    val decryptedInputStream = (inputMedia.encryption?.decryptInputStream(inputStream) ?: inputStream).buffered()
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    var read: Int
                    var totalRead = 0L
                    var lastTotalRead = 0L

                    outputStream().use { outputStream ->
                        while (decryptedInputStream.read(buffer).also { read = it } != -1) {
                            outputStream.write(buffer, 0, read)
                            totalRead += read
                            inputMediaDownloadedBytes[inputMedia] = totalRead
                            if (totalRead - lastTotalRead > 1024 * 1024) {
                                setProgress("${totalRead / 1024}KB/${estimatedSize / 1024}KB")
                                lastTotalRead = totalRead
                            }
                        }
                    }
                }.also { downloadedMedias[inputMedia] = it }
            }

            launch {
                when (inputMedia.type) {
                    DownloadMediaType.PROTO_MEDIA -> {
                        RemoteMediaResolver.downloadBoltMedia(Base64.UrlSafe.decode(inputMedia.content), decryptionCallback = { it }, resultCallback = { inputStream, length ->
                            totalSize += length
                            inputStream.use {
                                handleInputStream(it, estimatedSize = length)
                            }
                        })
                    }
                    DownloadMediaType.REMOTE_MEDIA -> {
                        with(URL(inputMedia.content).openConnection() as HttpURLConnection) {
                            requestMethod = "GET"
                            setRequestProperty("User-Agent", Constants.USER_AGENT)
                            connect()
                            totalSize += contentLength.toLong()
                            inputStream.use {
                                handleInputStream(it, estimatedSize = contentLength.toLong())
                            }
                        }
                    }
                    DownloadMediaType.DIRECT_MEDIA -> {
                        val decoded = Base64.UrlSafe.decode(inputMedia.content)
                        createMediaTempFile().apply {
                            writeBytes(decoded)
                        }.also { downloadedMedias[inputMedia] = it }
                    }
                    else -> {
                        downloadedMedias[inputMedia] = File(inputMedia.content)
                    }
                }
            }.also { jobs.add(it) }
        }

        jobs.joinAll()
        downloadedMedias
    }

    private suspend fun downloadRemoteMedia(pendingTask: PendingTask, metadata: DownloadMetadata, downloadedMedias: Map<InputMedia, DownloadedFile>, downloadRequest: DownloadRequest) {
        downloadRequest.inputMedias.first().let { inputMedia ->
            val mediaType = inputMedia.type
            val media = downloadedMedias[inputMedia]!!

            if (!downloadRequest.isDashPlaylist) {
                if (inputMedia.attachmentType == "NOTE") {
                    remoteSideContext.config.root.downloader.forceVoiceNoteFormat.getNullable()?.let { format ->
                        val outputFile = File.createTempFile("voice_note", ".$format")
                        newFFMpegProcessor(pendingTask).execute(FFMpegProcessor.Request(
                            action = FFMpegProcessor.Action.AUDIO_CONVERSION,
                            inputs = listOf(media.file),
                            output = outputFile
                        ))
                        media.file.delete()
                        saveMediaToGallery(pendingTask, outputFile, metadata)
                        outputFile.delete()
                        return
                    }
                }

                saveMediaToGallery(pendingTask, media.file, metadata)
                media.file.delete()
                return
            }

            assert(mediaType == DownloadMediaType.REMOTE_MEDIA)

            val playlistXml = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(media.file)
            val baseUrlNodeList = playlistXml.getElementsByTagName("BaseURL")
            for (i in 0 until baseUrlNodeList.length) {
                val baseUrlNode = baseUrlNodeList.item(i)
                val baseUrl = baseUrlNode.textContent
                baseUrlNode.textContent = "${RemoteMediaResolver.CF_ST_CDN_D}$baseUrl"
            }

            val dashOptions = downloadRequest.dashOptions!!

            val dashPlaylistFile = renameFromFileType(media.file, FileType.MPD)
            dashPlaylistFile.outputStream().use {
                TransformerFactory.newInstance().newTransformer().transform(DOMSource(playlistXml), StreamResult(it))
            }

            callbackOnProgress(translation.format("download_toast", "path" to dashPlaylistFile.nameWithoutExtension))
            val outputFile = File.createTempFile("dash", ".mp4")
            runCatching {
                newFFMpegProcessor(pendingTask).execute(FFMpegProcessor.Request(
                    action = FFMpegProcessor.Action.DOWNLOAD_DASH,
                    inputs = listOf(dashPlaylistFile),
                    output = outputFile,
                    startTime = dashOptions.offsetTime,
                    duration = dashOptions.duration
                ))
                saveMediaToGallery(pendingTask, outputFile, metadata)
            }.onFailure { exception ->
                if (coroutineContext.job.isCancelled) return@onFailure
                remoteSideContext.log.error("Failed to download dash media", exception)
                callbackOnFailure(translation.format("failed_processing_toast", "error" to exception.toString()), exception.message)
                pendingTask.fail("Failed to download dash media")
            }

            dashPlaylistFile.delete()
            outputFile.delete()
            media.file.delete()
        }
    }

    private fun renameFromFileType(file: File, fileType: FileType): File {
        val newFile = File(file.parentFile, file.nameWithoutExtension + "." + fileType.fileExtension)
        file.renameTo(newFile)
        return newFile
    }

    fun enqueue(downloadRequest: DownloadRequest, downloadMetadata: DownloadMetadata) {
        remoteSideContext.coroutineScope.launch {
            remoteSideContext.taskManager.getTaskByHash(downloadMetadata.mediaIdentifier)?.let { task ->
                remoteSideContext.log.debug("already queued or downloaded")

                if (task.status.isFinalStage()) {
                    if (task.status != TaskStatus.SUCCESS) return@let
                    // check if the media file has been deleted
                    if (task.type == TaskType.DOWNLOAD) {
                        val outputFile = runCatching {
                            DocumentFile.fromTreeUri(remoteSideContext.androidContext, Uri.parse(task.extra))
                        }.getOrNull()

                        if (outputFile != null && !outputFile.exists()) {
                            return@let
                        }
                    }
                    callbackOnFailure(translation["already_downloaded_toast"], null)
                } else {
                    callbackOnFailure(translation["already_queued_toast"], null)
                }
                return@launch
            }

            remoteSideContext.log.debug("downloading media")
            val pendingTask = remoteSideContext.taskManager.createPendingTask(
                Task(
                    type = TaskType.DOWNLOAD,
                    title = downloadMetadata.downloadSource,
                    author = downloadMetadata.mediaAuthor,
                    hash = downloadMetadata.mediaIdentifier
                )
            ).apply {
                status = TaskStatus.RUNNING
                addListener(PendingTaskListener(onCancel = {
                    coroutineContext.job.cancel()
                }))
                updateProgress("Downloading...")
            }

            runCatching {
                //first download all input medias into cache
                val downloadedMedias = downloadInputMedias(pendingTask, downloadRequest).map {
                    it.key to DownloadedFile(it.value, FileType.fromFile(it.value))
                }.toMap().toMutableMap()
                remoteSideContext.log.verbose("downloaded ${downloadedMedias.size} medias")

                var shouldMergeOverlay = downloadRequest.shouldMergeOverlay

                //if there is a zip file, extract it and replace the downloaded media with the extracted ones
                downloadedMedias.values.find { it.fileType == FileType.ZIP }?.let { zipFile ->
                    val oldDownloadedMedias = downloadedMedias.toMap()
                    downloadedMedias.clear()

                    zipFile.file.inputStream().use { zipFileInputStream ->
                        MediaDownloaderHelper.getSplitElements(zipFileInputStream) { type, inputStream ->
                            createMediaTempFile().apply {
                                outputStream().use {
                                    inputStream.copyTo(it)
                                }
                            }.also {
                                downloadedMedias[InputMedia(
                                    type = DownloadMediaType.LOCAL_MEDIA,
                                    content = it.absolutePath,
                                    isOverlay = type == SplitMediaAssetType.OVERLAY
                                )] = DownloadedFile(it, FileType.fromFile(it))
                            }
                        }
                    }

                    oldDownloadedMedias.forEach { (_, value) ->
                        value.file.delete()
                    }

                    shouldMergeOverlay = true
                }

                if (shouldMergeOverlay) {
                    assert(downloadedMedias.size == 2)
                    val media = downloadedMedias.entries.first { !it.key.isOverlay }.value
                    val overlayMedia = downloadedMedias.entries.first { it.key.isOverlay }.value

                    val renamedMedia = renameFromFileType(media.file, media.fileType)
                    val renamedOverlayMedia = renameFromFileType(overlayMedia.file, overlayMedia.fileType)
                    val mergedOverlay: File = File.createTempFile("merged", ".mp4")
                    runCatching {
                        callbackOnProgress(translation.format("processing_toast", "path" to media.file.nameWithoutExtension))

                        newFFMpegProcessor(pendingTask).execute(FFMpegProcessor.Request(
                            action = FFMpegProcessor.Action.MERGE_OVERLAY,
                            inputs = listOf(renamedMedia),
                            output = mergedOverlay,
                            overlay = renamedOverlayMedia
                        ))

                        saveMediaToGallery(pendingTask, mergedOverlay, downloadMetadata)
                    }.onFailure { exception ->
                        if (coroutineContext.job.isCancelled) return@onFailure
                        remoteSideContext.log.error("Failed to merge overlay", exception)
                        callbackOnFailure(translation.format("failed_processing_toast", "error" to exception.toString()), exception.message)
                        pendingTask.fail("Failed to merge overlay")
                    }

                    mergedOverlay.delete()
                    renamedOverlayMedia.delete()
                    renamedMedia.delete()
                    return@launch
                }

                downloadRemoteMedia(pendingTask, downloadMetadata, downloadedMedias, downloadRequest)
            }.onFailure { exception ->
                pendingTask.fail("Failed to download media")
                remoteSideContext.log.error("Failed to download media", exception)
                callbackOnFailure(translation["failed_generic_toast"], exception.message)
            }
        }
    }

    fun onReceive(intent: Intent) {
        val downloadMetadata = gson.fromJson(intent.getStringExtra(ReceiversConfig.DOWNLOAD_METADATA_EXTRA)!!, DownloadMetadata::class.java)
        val downloadRequest = gson.fromJson(intent.getStringExtra(ReceiversConfig.DOWNLOAD_REQUEST_EXTRA)!!, DownloadRequest::class.java)

        enqueue(downloadRequest, downloadMetadata)
    }
}
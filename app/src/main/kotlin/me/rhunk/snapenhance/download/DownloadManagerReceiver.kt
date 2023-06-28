package me.rhunk.snapenhance.download

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Handler
import android.widget.Toast
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.job
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import me.rhunk.snapenhance.Constants
import me.rhunk.snapenhance.Logger
import me.rhunk.snapenhance.SharedContext
import me.rhunk.snapenhance.data.FileType
import me.rhunk.snapenhance.download.data.DownloadRequest
import me.rhunk.snapenhance.download.data.InputMedia
import me.rhunk.snapenhance.download.data.MediaEncryptionKeyPair
import me.rhunk.snapenhance.download.data.PendingDownload
import me.rhunk.snapenhance.download.enums.DownloadMediaType
import me.rhunk.snapenhance.download.enums.DownloadStage
import me.rhunk.snapenhance.util.download.RemoteMediaResolver
import me.rhunk.snapenhance.util.snap.MediaDownloaderHelper
import java.io.File
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.zip.ZipInputStream
import javax.crypto.Cipher
import javax.crypto.CipherInputStream
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec
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
 * DownloadManagerReceiver handles the download requests of the user
 */
@OptIn(ExperimentalEncodingApi::class)
class DownloadManagerReceiver : BroadcastReceiver() {
    companion object {
        const val DOWNLOAD_ACTION = "me.rhunk.snapenhance.download.DownloadManagerReceiver.DOWNLOAD_ACTION"
    }

    private val translation by lazy {
        SharedContext.translation.getCategory("download_manager_receiver")
    }

    private lateinit var context: Context

    private fun runOnUIThread(block: () -> Unit) {
        Handler(context.mainLooper).post(block)
    }

    private fun shortToast(text: String) {
        runOnUIThread {
            Toast.makeText(context, text, Toast.LENGTH_SHORT).show()
        }
    }

    private fun longToast(text: String) {
        runOnUIThread {
            Toast.makeText(context, text, Toast.LENGTH_LONG).show()
        }
    }

    private fun extractZip(inputStream: InputStream): List<File> {
        val files = mutableListOf<File>()
        val zipInputStream = ZipInputStream(inputStream)
        var entry = zipInputStream.nextEntry

        while (entry != null) {
            createMediaTempFile().also { file ->
                file.outputStream().use { outputStream ->
                    zipInputStream.copyTo(outputStream)
                }
                files += file
            }
            entry = zipInputStream.nextEntry
        }

        return files
    }

    private fun decryptInputStream(inputStream: InputStream, encryption: MediaEncryptionKeyPair): InputStream {
        val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
        val key = Base64.UrlSafe.decode(encryption.key)
        val iv = Base64.UrlSafe.decode(encryption.iv)
        cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), IvParameterSpec(iv))
        return CipherInputStream(inputStream, cipher)
    }

    private fun createNeededDirectories(file: File): File {
        val directory = file.parentFile ?: return file
        if (!directory.exists()) {
            directory.mkdirs()
        }
        return file
    }

    @SuppressLint("UnspecifiedRegisterReceiverFlag")
    private suspend fun saveMediaToGallery(inputFile: File, pendingDownload: PendingDownload) {
        if (coroutineContext.job.isCancelled) return

        runCatching {
            val fileType = FileType.fromFile(inputFile)
            if (fileType == FileType.UNKNOWN) {
                longToast(translation.format("failed_gallery_toast", "error" to "Unknown media type"))
                return
            }
            val outputFile = File(pendingDownload.outputPath + "." + fileType.fileExtension).also { createNeededDirectories(it) }

            inputFile.copyTo(outputFile, overwrite = true)

            pendingDownload.outputFile = outputFile.absolutePath
            pendingDownload.downloadStage = DownloadStage.SAVED

            runCatching {
                val contentUri = Uri.fromFile(outputFile)
                val mediaScanIntent = Intent("android.intent.action.MEDIA_SCANNER_SCAN_FILE")
                mediaScanIntent.setData(contentUri)
                context.sendBroadcast(mediaScanIntent)
            }.onFailure {
                Logger.error("Failed to scan media file", it)
                longToast(translation.format("failed_gallery_toast", "error" to it.toString()))
            }

            Logger.debug("download complete")

            //print the path of the saved media
            val parentName = outputFile.parentFile?.parentFile?.absolutePath?.let {
                if (!it.endsWith("/")) "$it/" else it
            }

            shortToast(
                translation.format("saved_toast", "path" to outputFile.absolutePath.replace(parentName ?: "", ""))
            )
        }.onFailure {
            Logger.error(it)
            longToast(translation.format("failed_gallery_toast", "error" to it.toString()))
            pendingDownload.downloadStage = DownloadStage.FAILED
        }
    }

    private fun createMediaTempFile(): File {
        return File.createTempFile("media", ".tmp")
    }

    private fun downloadInputMedias(downloadRequest: DownloadRequest) = runBlocking {
        val jobs = mutableListOf<Job>()
        val downloadedMedias = mutableMapOf<InputMedia, File>()

        downloadRequest.getInputMedias().forEach { inputMedia ->
            fun handleInputStream(inputStream: InputStream) {
                createMediaTempFile().apply {
                    if (inputMedia.encryption != null) {
                        decryptInputStream(inputStream, inputMedia.encryption).use { decryptedInputStream ->
                            decryptedInputStream.copyTo(outputStream())
                        }
                    } else {
                        inputStream.copyTo(outputStream())
                    }
                }.also { downloadedMedias[inputMedia] = it }
            }

            launch {
                when (inputMedia.type) {
                    DownloadMediaType.PROTO_MEDIA -> {
                        RemoteMediaResolver.downloadBoltMedia(Base64.UrlSafe.decode(inputMedia.content))?.let { inputStream ->
                            handleInputStream(inputStream)
                        }
                    }
                    DownloadMediaType.DIRECT_MEDIA -> {
                        val decoded = Base64.UrlSafe.decode(inputMedia.content)
                        createMediaTempFile().apply {
                            writeBytes(decoded)
                        }.also { downloadedMedias[inputMedia] = it }
                    }
                    DownloadMediaType.REMOTE_MEDIA -> {
                        with(URL(inputMedia.content).openConnection() as HttpURLConnection) {
                            requestMethod = "GET"
                            setRequestProperty("User-Agent", Constants.USER_AGENT)
                            connect()
                            handleInputStream(inputStream)
                        }
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

    private suspend fun downloadRemoteMedia(pendingDownloadObject:  PendingDownload, downloadedMedias: Map<InputMedia, DownloadedFile>, downloadRequest: DownloadRequest) {
        downloadRequest.getInputMedias().first().let { inputMedia ->
            val mediaType = downloadRequest.getInputType(0)!!
            val media = downloadedMedias[inputMedia]!!

            if (!downloadRequest.isDashPlaylist) {
                saveMediaToGallery(media.file, pendingDownloadObject)
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

            val dashOptions = downloadRequest.getDashOptions()!!

            val dashPlaylistFile = renameFromFileType(media.file, FileType.MPD)
            val xmlData = dashPlaylistFile.outputStream()
            TransformerFactory.newInstance().newTransformer().transform(DOMSource(playlistXml), StreamResult(xmlData))

            longToast(translation.format("download_toast", "path" to dashPlaylistFile.nameWithoutExtension))
            val outputFile = File.createTempFile("dash", ".mp4")
            runCatching {
                MediaDownloaderHelper.downloadDashChapterFile(
                    dashPlaylist = dashPlaylistFile,
                    output = outputFile,
                    startTime = dashOptions.offsetTime,
                    duration = dashOptions.duration)
                saveMediaToGallery(outputFile, pendingDownloadObject)
            }.onFailure {
                if (coroutineContext.job.isCancelled) return@onFailure
                Logger.error(it)
                longToast(translation.format("failed_processing_toast", "error" to it.toString()))
                pendingDownloadObject.downloadStage = DownloadStage.FAILED
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

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != DOWNLOAD_ACTION) return
        this.context = context
        Logger.debug("onReceive download")

        SharedContext.ensureInitialized(context)

        val downloadRequest = DownloadRequest.fromBundle(intent.extras!!)

        SharedContext.downloadTaskManager.canDownloadMedia(downloadRequest.getUniqueHash())?.let { downloadStage ->
            shortToast(
                translation[if (downloadStage.isFinalStage) {
                    "already_downloaded_toast"
                } else {
                    "already_queued_toast"
                }]
            )
            return
        }

        CoroutineScope(Dispatchers.IO).launch {
            val pendingDownloadObject = PendingDownload.fromBundle(intent.extras!!)

            SharedContext.downloadTaskManager.addTask(pendingDownloadObject)
            pendingDownloadObject.apply {
                job = coroutineContext.job
                downloadStage = DownloadStage.DOWNLOADING
            }

            runCatching {
                //first download all input medias into cache
                val downloadedMedias = downloadInputMedias(downloadRequest).map {
                    it.key to DownloadedFile(it.value, FileType.fromFile(it.value))
                }.toMap().toMutableMap()
                Logger.debug("downloaded ${downloadedMedias.size} medias")

                var shouldMergeOverlay = downloadRequest.shouldMergeOverlay

                //if there is a zip file, extract it and replace the downloaded media with the extracted ones
                downloadedMedias.values.find { it.fileType == FileType.ZIP }?.let { entry ->
                    val extractedMedias = extractZip(entry.file.inputStream()).map {
                        InputMedia(
                            type = DownloadMediaType.LOCAL_MEDIA,
                            content = it.absolutePath
                        ) to DownloadedFile(it, FileType.fromFile(it))
                    }

                    downloadedMedias.values.removeIf {
                        it.file.delete()
                        true
                    }

                    downloadedMedias.putAll(extractedMedias)
                    shouldMergeOverlay = true
                }

                if (shouldMergeOverlay) {
                    assert(downloadedMedias.size == 2)
                    val media = downloadedMedias.values.first { it.fileType.isVideo }
                    val overlayMedia = downloadedMedias.values.first { it.fileType.isImage }

                    val renamedMedia = renameFromFileType(media.file, media.fileType)
                    val renamedOverlayMedia = renameFromFileType(overlayMedia.file, overlayMedia.fileType)
                    val mergedOverlay: File = File.createTempFile("merged", "." + media.fileType.fileExtension)
                    runCatching {
                        longToast(translation.format("download_toast", "path" to media.file.nameWithoutExtension))
                        pendingDownloadObject.downloadStage = DownloadStage.MERGING

                        MediaDownloaderHelper.mergeOverlayFile(
                            media = renamedMedia,
                            overlay = renamedOverlayMedia,
                            output = mergedOverlay
                        )

                        saveMediaToGallery(mergedOverlay, pendingDownloadObject)
                    }.onFailure {
                        if (coroutineContext.job.isCancelled) return@onFailure
                        Logger.error(it)
                        longToast(translation.format("failed_processing_toast", "error" to it.toString()))
                        pendingDownloadObject.downloadStage = DownloadStage.MERGE_FAILED
                    }

                    mergedOverlay.delete()
                    renamedOverlayMedia.delete()
                    renamedMedia.delete()
                    return@launch
                }

                downloadRemoteMedia(pendingDownloadObject, downloadedMedias, downloadRequest)
            }.onFailure {
                pendingDownloadObject.downloadStage = DownloadStage.FAILED
                Logger.error(it)
                longToast(translation["failed_generic_toast"])
            }
        }
    }
}
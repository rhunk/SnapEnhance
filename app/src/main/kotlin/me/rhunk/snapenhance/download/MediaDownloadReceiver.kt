package me.rhunk.snapenhance.download

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.media.MediaScannerConnection
import android.os.Handler
import android.widget.Toast
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.job
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import me.rhunk.snapenhance.Constants
import me.rhunk.snapenhance.Logger
import me.rhunk.snapenhance.data.FileType
import me.rhunk.snapenhance.util.MediaDownloaderHelper
import me.rhunk.snapenhance.util.download.RemoteMediaResolver
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
 * MediaDownloadReceiver handles the download of media files
 */
@OptIn(ExperimentalEncodingApi::class)
class MediaDownloadReceiver : BroadcastReceiver() {
    companion object {
        val downloadTasks = mutableListOf<PendingDownload>()

        const val DOWNLOAD_ACTION = "me.rhunk.snapenhance.download.MediaDownloadReceiver.DOWNLOAD_ACTION"
    }

    lateinit var context: Context

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

    private fun decryptInputStream(inputStream: InputStream, encryption: Pair<String, String>): InputStream {
        val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
        val key = Base64.UrlSafe.decode(encryption.first)
        val iv = Base64.UrlSafe.decode(encryption.second)
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

    private suspend fun saveMediaToGallery(inputFile: File, pendingDownload: PendingDownload) {
        if (coroutineContext.job.isCancelled) return

        runCatching {
            val fileType = FileType.fromFile(inputFile)
            val outputFile = File(pendingDownload.outputPath + "." + fileType.fileExtension).also { createNeededDirectories(it) }
            inputFile.copyTo(outputFile, overwrite = true)

            MediaScannerConnection.scanFile(context, arrayOf(outputFile.absolutePath), null, null)

            //print the path of the saved media
            val parentName = outputFile.parentFile?.parentFile?.absolutePath?.let {
                if (!it.endsWith("/")) "$it/" else it
            }

            longToast("Saved media to ${outputFile.absolutePath.replace(parentName ?: "", "")}")

            pendingDownload.outputFile = outputFile.absolutePath
            pendingDownload.downloadStage = DownloadStage.SAVED
        }.onFailure {
            Logger.error("Failed to save media to gallery", it)
            longToast("Failed to save media to gallery")
            pendingDownload.downloadStage = DownloadStage.FAILED
        }
    }

    private fun createMediaTempFile(): File {
        return File.createTempFile("media", ".tmp")
    }

    private fun downloadInputMedias(inputMedias: List<String>, inputMediaTypes: List<DownloadMediaType>, mediaEncryption: Map<String, Pair<String, String>>) = runBlocking {
        val jobs = mutableListOf<Job>()
        val downloadedMedias = mutableMapOf<String, File>()

        inputMedias.forEachIndexed { index, mediaData ->
            val mediaType = inputMediaTypes[index]
            val hasEncryption = mediaEncryption.containsKey(mediaData)

            fun handleInputStream(inputStream: InputStream) {
                createMediaTempFile().apply {
                    if (hasEncryption) {
                        decryptInputStream(inputStream, mediaEncryption[mediaData]!!).use { decryptedInputStream ->
                            decryptedInputStream.copyTo(outputStream())
                        }
                    } else {
                        inputStream.copyTo(outputStream())
                    }
                }.also { downloadedMedias[mediaData] = it }
            }

            launch {
                when (mediaType) {
                    DownloadMediaType.PROTO_MEDIA -> {
                        RemoteMediaResolver.downloadBoltMedia(Base64.UrlSafe.decode(mediaData))?.let { inputStream ->
                            handleInputStream(inputStream)
                        }
                    }
                    DownloadMediaType.DIRECT_MEDIA -> {
                        val decoded = Base64.UrlSafe.decode(mediaData)
                        createMediaTempFile().apply {
                            writeBytes(decoded)
                        }.also { downloadedMedias[mediaData] = it }
                    }
                    DownloadMediaType.REMOTE_MEDIA -> {
                        with(URL(mediaData).openConnection() as HttpURLConnection) {
                            requestMethod = "GET"
                            setRequestProperty("User-Agent", Constants.USER_AGENT)
                            connect()
                            handleInputStream(inputStream)
                        }
                    }
                    else -> {
                        downloadedMedias[mediaData] = File(mediaData)
                    }
                }
            }.also { jobs.add(it) }
        }

        jobs.joinAll()
        downloadedMedias
    }

    private fun renameFromFileType(file: File, fileType: FileType): File {
        val newFile = File(file.parentFile, file.nameWithoutExtension + "." + fileType.fileExtension)
        file.renameTo(newFile)
        return newFile
    }

    @OptIn(DelicateCoroutinesApi::class)
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != DOWNLOAD_ACTION) return
        this.context = context

        val inputMedias = intent.getStringArrayExtra("inputMedias") ?: return
        val inputMediaTypes = intent.getStringArrayExtra("inputTypes")?.map { DownloadMediaType.valueOf(it) } ?: return
        val mediaEncryption = intent.getStringArrayExtra("mediaEncryption")?.associate { entry ->
            entry.split("|").let {
                it[0] to Pair(it[1], it[2])
            }
        } ?: return

        var shouldMergeOverlay = intent.getBooleanExtra("shouldMergeOverlay", false)
        val isDashPlaylist = intent.getBooleanExtra("isDashPlaylist", false)

        GlobalScope.launch(Dispatchers.Default) {
            val pendingDownloadObject = PendingDownload(intent = intent)

            downloadTasks.add(0, pendingDownloadObject.apply {
                job = coroutineContext.job
                downloadStage = DownloadStage.DOWNLOADING
            })

            runCatching {
                //first download all input medias into cache
                val downloadedMedias = downloadInputMedias(inputMedias.toList(), inputMediaTypes.toList(), mediaEncryption).map {
                    it.key to DownloadedFile(it.value, FileType.fromFile(it.value))
                }.toMap().toMutableMap()

                //if there is a zip file, extract it and replace the downloaded media with the extracted ones
                downloadedMedias.values.find { it.fileType == FileType.ZIP }?.let { entry ->
                    val extractedMedias = extractZip(entry.file.inputStream()).map {
                        it.absolutePath to DownloadedFile(it, FileType.fromFile(it))
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
                        longToast("Merging overlay...")
                        pendingDownloadObject.downloadStage = DownloadStage.MERGING

                        MediaDownloaderHelper.mergeOverlayFile(
                            media = renamedMedia,
                            overlay = renamedOverlayMedia,
                            output = mergedOverlay
                        )

                        saveMediaToGallery(mergedOverlay, pendingDownloadObject)
                    }.onFailure {
                        if (coroutineContext.job.isCancelled) return@onFailure
                        Logger.error("failed to merge overlay", it)
                        longToast("Failed to merge overlay: ${it.message}")
                        pendingDownloadObject.downloadStage = DownloadStage.MERGE_FAILED
                    }

                    mergedOverlay.delete()
                    renamedOverlayMedia.delete()
                    renamedMedia.delete()
                    return@launch
                }

                inputMedias[0]?.let { inputMedia ->
                    val mediaType = inputMediaTypes[0]
                    val media = downloadedMedias[inputMedia]!!

                    if (!isDashPlaylist) {
                        saveMediaToGallery(media.file, pendingDownloadObject)
                        media.file.delete()
                        return@launch
                    }

                    assert(mediaType == DownloadMediaType.REMOTE_MEDIA)

                    val playlistXml = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(media.file)
                    val baseUrlNodeList = playlistXml.getElementsByTagName("BaseURL")
                    for (i in 0 until baseUrlNodeList.length) {
                        val baseUrlNode = baseUrlNodeList.item(i)
                        val baseUrl = baseUrlNode.textContent
                        baseUrlNode.textContent = "${RemoteMediaResolver.CF_ST_CDN_D}$baseUrl"
                    }

                    val dashOptions = intent.getBundleExtra("dashOptions") ?: return@launch
                    val offsetTime = dashOptions.getLong("offsetTime")
                    val duration = dashOptions.getLong("duration", -1L).let { if (it == -1L) null else it }

                    val dashPlaylistFile = renameFromFileType(media.file, FileType.MPD)
                    val xmlData = dashPlaylistFile.outputStream()
                    TransformerFactory.newInstance().newTransformer().transform(DOMSource(playlistXml), StreamResult(xmlData))

                    longToast("Downloading dash media...")
                    val outputFile = File.createTempFile("dash", ".mp4")
                    runCatching {
                        MediaDownloaderHelper.downloadDashChapterFile(
                            dashPlaylist = dashPlaylistFile,
                            output = outputFile,
                            startTime = offsetTime,
                            duration = duration)
                        saveMediaToGallery(outputFile, pendingDownloadObject)
                    }.onFailure {
                        if (coroutineContext.job.isCancelled) return@onFailure
                        Logger.error("failed to download dash media", it)
                        longToast("Failed to download dash media: ${it.message}")
                        pendingDownloadObject.downloadStage = DownloadStage.FAILED
                    }

                    dashPlaylistFile.delete()
                    outputFile.delete()
                    media.file.delete()
                }
            }.onFailure {
                pendingDownloadObject.downloadStage = DownloadStage.FAILED
                Logger.error("failed to download media", it)
                longToast("Failed to download media: ${it.message}")
            }
        }
    }
}
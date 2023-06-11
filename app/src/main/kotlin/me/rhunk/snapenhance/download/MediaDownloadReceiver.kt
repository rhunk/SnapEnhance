package me.rhunk.snapenhance.download

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Handler
import android.widget.Toast
import kotlinx.coroutines.Job
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
import java.nio.file.Paths
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicReference
import javax.crypto.Cipher
import javax.crypto.CipherInputStream
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec
import javax.xml.parsers.DocumentBuilderFactory
import javax.xml.transform.TransformerFactory
import javax.xml.transform.dom.DOMSource
import javax.xml.transform.stream.StreamResult
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlin.io.path.inputStream

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
        const val DOWNLOAD_ACTION = "me.rhunk.snapenhance.download.MediaDownloadReceiver.DOWNLOAD_ACTION"
    }

    private val executor = Executors.newCachedThreadPool()

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

    private fun queryMedia(uri: String, type: DownloadMediaType, encryption: Pair<String, String>?): InputStream? {
        val mediaUri = Uri.parse(uri)
        val mediaInputStream = AtomicReference<InputStream>()
        if (mediaUri.scheme == "file") {
            mediaInputStream.set(Paths.get(mediaUri.path).inputStream())
        } else {
            val url = URL(mediaUri.toString())
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.setRequestProperty("User-Agent", Constants.USER_AGENT)
            connection.connect()
            mediaInputStream.set(connection.inputStream)
        }
        encryption?.let {
            val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
            mediaInputStream.set(
                CipherInputStream(mediaInputStream.get(), cipher).apply {
                    cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(it.first.toByteArray(), "AES"), IvParameterSpec(it.second.toByteArray()))
                }
            )
        }
        return mediaInputStream.get()
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

    private fun saveMediaToGallery(inputFile: File, outputPath: String) {
        val fileType = getFileType(inputFile)
        val outputFile = File(outputPath + "." + fileType.fileExtension).also { createNeededDirectories(it) }
        inputFile.copyTo(outputFile, overwrite = true)
        //print the path of the saved media
        val parentName = outputFile.parentFile?.parentFile?.absolutePath?.let {
            if (!it.endsWith("/")) "$it/" else it
        }
        longToast("Saved media to ${outputFile.absolutePath.replace(parentName ?: "", "")}")
    }

    private fun getFileType(file: File): FileType {
        file.inputStream().use { inputStream ->
            val buffer = ByteArray(16)
            inputStream.read(buffer)
            return FileType.fromByteArray(buffer)
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
                Logger.debug("Downloading media $mediaData hasEncryption=$hasEncryption")
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
                        val urlConnection = URL(mediaData).openConnection() as HttpURLConnection
                        urlConnection.requestMethod = "GET"
                        urlConnection.setRequestProperty("User-Agent", Constants.USER_AGENT)
                        urlConnection.connect()
                        handleInputStream(urlConnection.inputStream)
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
    private fun executeAsync(block: () -> Unit) {
        executor.execute{
            runCatching {
                block()
            }.onFailure {
                Logger.error(it)
                longToast("Error: ${it.message}")
            }
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != DOWNLOAD_ACTION) return
        this.context = context
        val outputPath = intent.getStringExtra("outputPath") ?: return

        val inputMedias = intent.getStringArrayExtra("inputMedias") ?: return
        val inputMediaTypes = intent.getStringArrayExtra("inputTypes")?.map { DownloadMediaType.valueOf(it) } ?: return
        val mediaEncryption = intent.getStringArrayExtra("mediaEncryption")?.associate { entry ->
            entry.split("|").let {
                it[0] to Pair(it[1], it[2])
            }
        } ?: return

        val shouldMergeOverlay = intent.getBooleanExtra("shouldMergeOverlay", false)
        val isDashPlaylist = intent.getBooleanExtra("isDashPlaylist", false)

        executeAsync {
            //first download all input medias into cache
            val downloadedMedias = downloadInputMedias(inputMedias.toList(), inputMediaTypes.toList(), mediaEncryption).map {
                it.key to DownloadedFile(it.value, getFileType(it.value))
            }.toMap().toMutableMap()

            if (shouldMergeOverlay) {
                assert(downloadedMedias.size == 2)
                val media = downloadedMedias.values.first { it.fileType.isVideo }
                val overlayMedia = downloadedMedias.values.first { it.fileType.isImage }

                val renamedMedia = renameFromFileType(media.file, media.fileType)
                val renamedOverlayMedia = renameFromFileType(overlayMedia.file, overlayMedia.fileType)

                runCatching {
                    longToast("Merging overlay...")
                    val mergedOverlay = MediaDownloaderHelper.mergeOverlayFile(
                        renamedMedia,
                        renamedOverlayMedia,
                        media.fileType
                    )

                    media.file.delete()
                    renamedMedia.delete()
                    renamedOverlayMedia.delete()

                    Logger.debug("merged overlay ${mergedOverlay.absolutePath}")
                    saveMediaToGallery(mergedOverlay, outputPath)
                    mergedOverlay.delete()
                }.onFailure {
                    Logger.error("failed to merge overlay", it)
                    longToast("Failed to merge overlay: ${it.message}")
                }

                renamedOverlayMedia.delete()
                renamedMedia.delete()

                return@executeAsync
            }

            inputMedias[0]?.let {
                val mediaType = inputMediaTypes[0]
                val media = downloadedMedias[it]!!

                if (isDashPlaylist) {
                    assert(mediaType == DownloadMediaType.REMOTE_MEDIA)

                    val playlistXml = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(media.file)
                    val baseUrlNodeList = playlistXml.getElementsByTagName("BaseURL")
                    for (i in 0 until baseUrlNodeList.length) {
                        val baseUrlNode = baseUrlNodeList.item(i)
                        val baseUrl = baseUrlNode.textContent
                        baseUrlNode.textContent = "${RemoteMediaResolver.CF_ST_CDN_D}$baseUrl"
                    }

                    val dashOptions = intent.getBundleExtra("dashOptions") ?: return@executeAsync
                    val offsetTimestamp = dashOptions.getLong("offsetTimestamp")
                    val duration = dashOptions.getLong("duration")

                    val dashPlaylistFile = renameFromFileType(media.file, FileType.MPD)
                    val xmlData = dashPlaylistFile.outputStream()
                    TransformerFactory.newInstance().newTransformer().transform(DOMSource(playlistXml), StreamResult(xmlData))

                    longToast("Downloading dash media...")
                    val outputFile = MediaDownloaderHelper.downloadDashChapterFile(dashPlaylistFile, offsetTimestamp, duration)
                    dashPlaylistFile.delete()

                    saveMediaToGallery(outputFile, outputPath)
                    outputFile.delete()
                    return@executeAsync
                }

                saveMediaToGallery(media.file, outputPath)
                media.file.delete()
            }
        }
    }
}
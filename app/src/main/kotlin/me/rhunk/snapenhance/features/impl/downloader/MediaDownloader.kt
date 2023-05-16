package me.rhunk.snapenhance.features.impl.downloader

import android.app.AlertDialog
import android.content.DialogInterface
import android.graphics.Bitmap
import android.media.MediaScannerConnection
import android.net.Uri
import android.widget.ImageView
import com.arthenica.ffmpegkit.FFmpegKit
import me.rhunk.snapenhance.Constants
import me.rhunk.snapenhance.Constants.ARROYO_URL_KEY_PROTO_PATH
import me.rhunk.snapenhance.Logger
import me.rhunk.snapenhance.Logger.xposedLog
import me.rhunk.snapenhance.config.ConfigProperty
import me.rhunk.snapenhance.data.ContentType
import me.rhunk.snapenhance.data.FileType
import me.rhunk.snapenhance.data.wrapper.impl.media.MediaInfo
import me.rhunk.snapenhance.data.wrapper.impl.media.opera.Layer
import me.rhunk.snapenhance.data.wrapper.impl.media.opera.ParamMap
import me.rhunk.snapenhance.features.Feature
import me.rhunk.snapenhance.features.FeatureLoadParams
import me.rhunk.snapenhance.features.impl.Messaging
import me.rhunk.snapenhance.features.impl.spy.MessageLogger
import me.rhunk.snapenhance.hook.HookAdapter
import me.rhunk.snapenhance.hook.HookStage
import me.rhunk.snapenhance.hook.Hooker
import me.rhunk.snapenhance.util.EncryptionUtils
import me.rhunk.snapenhance.util.PreviewUtils
import me.rhunk.snapenhance.util.download.CdnDownloader
import me.rhunk.snapenhance.util.getObjectField
import me.rhunk.snapenhance.util.protobuf.ProtoReader
import java.io.*
import java.net.HttpURLConnection
import java.net.URL
import java.nio.file.Paths
import java.util.*
import java.util.concurrent.atomic.AtomicReference
import java.util.zip.ZipInputStream
import javax.crypto.Cipher
import javax.crypto.CipherInputStream
import kotlin.io.path.inputStream

enum class MediaType {
    ORIGINAL, OVERLAY
}

class MediaDownloader : Feature("MediaDownloader", loadParams = FeatureLoadParams.ACTIVITY_CREATE_ASYNC) {
    private var lastSeenMediaInfoMap: MutableMap<MediaType, MediaInfo>? = null
    private var lastSeenMapParams: ParamMap? = null
    private var isFFmpegPresent: Boolean? = null

    private fun canMergeOverlay(): Boolean {
        if (!context.config.bool(ConfigProperty.OVERLAY_MERGE)) return false
        if (isFFmpegPresent != null) {
            return isFFmpegPresent!!
        }
        //check if ffmpeg is correctly installed
        isFFmpegPresent = runCatching { FFmpegKit.execute("-version") }.isSuccess
        return isFFmpegPresent!!
    }

    private fun createNewFilePath(hash: Int, author: String, fileType: FileType): String? {
        val hexHash = Integer.toHexString(hash)
        return author + "/" + hexHash + "." + fileType.fileExtension
    }

    private fun downloadFile(outputFile: File, content: ByteArray): Boolean {
        val onDownloadComplete = {
                context.shortToast(
                    "Saved to " + outputFile.absolutePath.replace(context.config.string(ConfigProperty.SAVE_FOLDER), "")
                        .substring(1)
                )
            }
        if (!context.config.bool(ConfigProperty.USE_DOWNLOAD_MANAGER)) {
            try {
                val fos = FileOutputStream(outputFile)
                fos.write(content)
                fos.close()
                MediaScannerConnection.scanFile(
                    context.androidContext,
                    arrayOf(outputFile.absolutePath),
                    null,
                    null
                )
                onDownloadComplete()
            } catch (e: Throwable) {
                xposedLog(e)
                context.longToast("Failed to save file: " + e.message)
                return false
            }
            return true
        }
        context.downloadServer.startFileDownload(outputFile, content) { result ->
            if (result) {
                onDownloadComplete()
                return@startFileDownload
            }
            context.longToast("Failed to save file. Check logs for more info.")
        }
        return true
    }


    private fun mergeOverlay(original: ByteArray, overlay: ByteArray, isPreviewMode: Boolean): ByteArray? {
        context.longToast("Merging current media with overlay. This may take a while.")
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
            context.longToast("Failed to merge video and overlay. See logs for more details.")
            xposedLog(fFmpegSession.output)
            return null
        }
        val mergedFileData: ByteArray = FileInputStream(mergedFile).readBytes()
        mergedFile.delete()
        return mergedFileData
    }

    private fun queryMediaData(mediaInfo: MediaInfo): ByteArray {
        val mediaUri = Uri.parse(mediaInfo.uri)
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
        mediaInfo.encryption?.let { encryption ->
            mediaInputStream.set(CipherInputStream(mediaInputStream.get(), encryption.newCipher(Cipher.DECRYPT_MODE)))
        }
        return mediaInputStream.get().readBytes()
    }

    private fun createNeededDirectories(file: File): File {
        val directory = file.parentFile ?: return file
        if (!directory.exists()) {
            directory.mkdirs()
        }
        return file
    }

    private fun isFileExists(hash: Int, author: String, fileType: FileType): Boolean {
        val fileName: String = createNewFilePath(hash, author, fileType) ?: return false
        val outputFile: File =
            createNeededDirectories(File(context.config.string(ConfigProperty.SAVE_FOLDER), fileName))
        return outputFile.exists()
    }


    /*
     * Download the last seen media
     */
    fun downloadLastOperaMediaAsync() {
        if (lastSeenMapParams == null || lastSeenMediaInfoMap == null) return
        context.executeAsync {
            handleOperaMedia(
                lastSeenMapParams!!,
                lastSeenMediaInfoMap!!, true
            )
        }
    }

    private fun downloadOperaMedia(mediaInfoMap: Map<MediaType, MediaInfo>, author: String) {
        if (mediaInfoMap.isEmpty()) return
        val originalMediaInfo = mediaInfoMap[MediaType.ORIGINAL]!!
        if (mediaInfoMap.containsKey(MediaType.OVERLAY)) {
            context.shortToast("Downloading split snap")
        }
        var mediaContent: ByteArray? = queryMediaData(originalMediaInfo)
        val hash = Arrays.hashCode(mediaContent)
        if (mediaInfoMap.containsKey(MediaType.OVERLAY)) {
            //prevent converting the same media twice
            if (isFileExists(hash, author, FileType.fromByteArray(mediaContent!!))) {
                context.shortToast("Media already exists")
                return
            }
            val overlayMediaInfo = mediaInfoMap[MediaType.OVERLAY]!!
            val overlayContent: ByteArray = queryMediaData(overlayMediaInfo)
            mediaContent = mergeOverlay(mediaContent, overlayContent, false)
        }
        val fileType = FileType.fromByteArray(mediaContent!!)
        downloadMediaContent(mediaContent, hash, author, fileType)
    }

    private fun downloadMediaContent(
        data: ByteArray,
        hash: Int,
        messageAuthor: String,
        fileType: FileType
    ): Boolean {
        val fileName: String = createNewFilePath(hash, messageAuthor, fileType) ?: return false
        val outputFile: File = createNeededDirectories(File(context.config.string(ConfigProperty.SAVE_FOLDER), fileName))
        if (outputFile.exists()) {
            context.shortToast("Media already exists")
            return false
        }
        return downloadFile(outputFile, data)
    }

    /**
     * Handles the media from the opera viewer
     *
     * @param paramMap      the parameters from the opera viewer
     * @param mediaInfoMap  the media info map
     * @param forceDownload if the media should be downloaded
     */
    private fun handleOperaMedia(
        paramMap: ParamMap,
        mediaInfoMap: Map<MediaType, MediaInfo>,
        forceDownload: Boolean
    ) {
        //messages
        if (paramMap.containsKey("MESSAGE_ID") &&
            (forceDownload || context.config.bool(ConfigProperty.AUTO_DOWNLOAD_SNAPS))) {
            val id = paramMap["MESSAGE_ID"].toString()
            val messageId = id.substring(id.lastIndexOf(":") + 1).toLong()
            val senderId: String = context.database.getConversationMessageFromId(messageId)!!.sender_id!!

            if (!forceDownload && context.feature(AntiAutoDownload::class).isUserIgnored(senderId)) {
                return
            }

            val author = context.database.getFriendInfo(senderId)!!.usernameForSorting!!
            downloadOperaMedia(mediaInfoMap, author)
            return
        }

        //private stories
        val playlistV2Group =
            if (paramMap.containsKey("PLAYLIST_V2_GROUP")) paramMap["PLAYLIST_V2_GROUP"].toString() else null
        if (playlistV2Group != null &&
            playlistV2Group.contains("storyUserId=") &&
            (forceDownload || context.config.bool(ConfigProperty.AUTO_DOWNLOAD_STORIES))
        ) {
            val storyIdStartIndex = playlistV2Group.indexOf("storyUserId=") + 12
            val storyUserId = playlistV2Group.substring(storyIdStartIndex, playlistV2Group.indexOf(",", storyIdStartIndex))
            val author = context.database.getFriendInfo(storyUserId)
            downloadOperaMedia(mediaInfoMap, author!!.usernameForSorting!!)
            return
        }
        val snapSource = paramMap["SNAP_SOURCE"].toString()

        //public stories
        if (snapSource == "PUBLIC_USER" && (forceDownload || context.config.bool(ConfigProperty.AUTO_DOWNLOAD_PUBLIC_STORIES))) {
            val userDisplayName = (if (paramMap.containsKey("USER_DISPLAY_NAME")) paramMap["USER_DISPLAY_NAME"].toString() else "").replace(
                    "[^\\x00-\\x7F]".toRegex(),
                    "")
            downloadOperaMedia(mediaInfoMap, "Public-Stories/$userDisplayName")
        }

        //spotlight
        if (snapSource == "SINGLE_SNAP_STORY" && (forceDownload || context.config.bool(ConfigProperty.AUTO_DOWNLOAD_SPOTLIGHT))) {
            downloadOperaMedia(mediaInfoMap, "Spotlight")
        }
    }

    private fun canAutoDownload(): Boolean {
        return context.config.bool(ConfigProperty.AUTO_DOWNLOAD_SNAPS) ||
                context.config.bool(ConfigProperty.AUTO_DOWNLOAD_STORIES) ||
                context.config.bool(ConfigProperty.AUTO_DOWNLOAD_PUBLIC_STORIES) ||
                context.config.bool(ConfigProperty.AUTO_DOWNLOAD_SPOTLIGHT)
    }

    override fun asyncOnActivityCreate() {
        val operaViewerControllerClass: Class<*> = context.mappings.getMappedClass("OperaPageViewController", "Class")

        val onOperaViewStateCallback: (HookAdapter) -> Unit = onOperaViewStateCallback@{ param ->

            val viewState = (param.thisObject() as Any).getObjectField(context.mappings.getMappedValue("OperaPageViewController", "viewStateField")).toString()
            if (viewState != "FULLY_DISPLAYED") {
                return@onOperaViewStateCallback
            }
            val operaLayerList = (param.thisObject() as Any).getObjectField(context.mappings.getMappedValue("OperaPageViewController", "layerListField")) as ArrayList<*>
            val mediaParamMap: ParamMap = operaLayerList.map { Layer(it) }.first().paramMap

            if (!mediaParamMap.containsKey("image_media_info") && !mediaParamMap.containsKey("video_media_info_list"))
                return@onOperaViewStateCallback

            val mediaInfoMap = mutableMapOf<MediaType, MediaInfo>()
            val isVideo = mediaParamMap.containsKey("video_media_info_list")
            mediaInfoMap[MediaType.ORIGINAL] = MediaInfo(
                (if (isVideo) mediaParamMap["video_media_info_list"] else mediaParamMap["image_media_info"])!!
            )
            if (canMergeOverlay() && mediaParamMap.containsKey("overlay_image_media_info")) {
                mediaInfoMap[MediaType.OVERLAY] =
                    MediaInfo(mediaParamMap["overlay_image_media_info"]!!)
            }
            lastSeenMapParams = mediaParamMap
            lastSeenMediaInfoMap = mediaInfoMap

            if (!canAutoDownload()) return@onOperaViewStateCallback

            context.executeAsync {
                try {
                    handleOperaMedia(mediaParamMap, mediaInfoMap, false)
                } catch (e: Throwable) {
                    xposedLog(e)
                    context.longToast(e.message!!)
                }
            }
        }

        arrayOf("onDisplayStateChange", "onDisplayStateChange2").forEach { methodName ->
            Hooker.hook(
                operaViewerControllerClass,
                context.mappings.getMappedValue("OperaPageViewController", methodName),
                HookStage.AFTER, onOperaViewStateCallback
            )
        }
    }

    /**
     * Called when a message is focused in chat
     */
    //TODO: use snapchat classes instead of database (when content is deleted)
    fun onMessageActionMenu(isPreviewMode: Boolean) {
        //check if the message was focused in a conversation
        val messaging = context.feature(Messaging::class)
        if (messaging.lastOpenedConversationUUID == null) return
        val message = context.database.getConversationMessageFromId(messaging.lastFocusedMessageId) ?: return

        //get the message author
        val messageAuthor: String = context.database.getFriendInfo(message.sender_id!!)!!.usernameForSorting!!

        //check if the messageId
        val contentType: ContentType = ContentType.fromId(message.content_type)
        if (context.feature(MessageLogger::class).isMessageRemoved(message.client_message_id.toLong())) {
            context.shortToast("Preview/Download are not yet available for deleted messages")
            return
        }
        if (contentType != ContentType.NOTE &&
            contentType != ContentType.SNAP &&
            contentType != ContentType.EXTERNAL_MEDIA) {
            context.shortToast("Unsupported content type $contentType")
            return
        }
        val messageReader = ProtoReader(message.message_content!!)
        val urlKey: String = messageReader.getString(*ARROYO_URL_KEY_PROTO_PATH)!!

        //download the message content
        try {
            context.shortToast("Retriving message media")
            var inputStream: InputStream = CdnDownloader.downloadWithDefaultEndpoints(urlKey) ?: return
            inputStream = EncryptionUtils.decryptInputStreamFromArroyo(
                inputStream,
                contentType,
                messageReader
            )

            var mediaData: ByteArray = inputStream.readBytes()
            var fileType = FileType.fromByteArray(mediaData)
            val isZipFile = fileType == FileType.ZIP

            //videos with overlay are packed in a zip file
            //there are 2 files in the zip file, the video (webm) and the overlay (png)
            if (isZipFile) {
                var videoData: ByteArray? = null
                var overlayData: ByteArray? = null
                val zipInputStream = ZipInputStream(ByteArrayInputStream(mediaData))
                while (zipInputStream.nextEntry != null) {
                    val zipEntryData: ByteArray = zipInputStream.readBytes()
                    val entryFileType = FileType.fromByteArray(zipEntryData)
                    if (entryFileType.isVideo) {
                        videoData = zipEntryData
                    } else if (entryFileType.isImage) {
                        overlayData = zipEntryData
                    }
                }
                if (videoData == null || overlayData == null) {
                    xposedLog("Invalid data in zip file")
                    return
                }
                val mergedVideo = mergeOverlay(videoData, overlayData, isPreviewMode)
                val videoFileType = FileType.fromByteArray(videoData)
                if (!isPreviewMode) {
                    downloadMediaContent(
                        mergedVideo!!,
                        Arrays.hashCode(videoData),
                        messageAuthor,
                        videoFileType
                    )
                    return
                }
                mediaData = mergedVideo!!
                fileType = videoFileType
            }
            if (isPreviewMode) {
                runCatching {
                    val bitmap: Bitmap = PreviewUtils.createPreview(mediaData, fileType.isVideo)!!
                    val builder = AlertDialog.Builder(context.mainActivity)
                    builder.setTitle("Preview")
                    val imageView = ImageView(builder.context)
                    imageView.setImageBitmap(bitmap)
                    builder.setView(imageView)
                    builder.setPositiveButton(
                        "Close"
                    ) { dialog: DialogInterface, _: Int -> dialog.dismiss() }
                    context.runOnUiThread { builder.show() }
                }.onFailure {
                    context.shortToast("Failed to create preview: ${it.message}")
                    xposedLog(it)
                }
                return
            }
            downloadMediaContent(mediaData, mediaData.contentHashCode(), messageAuthor, fileType)
        } catch (e: FileNotFoundException) {
            context.shortToast("Unable to get $urlKey from cdn list. Check the logs for more info")
        } catch (e: Throwable) {
            context.shortToast("Failed to download " + e.message)
            xposedLog(e)
        }
    }
}
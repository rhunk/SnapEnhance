package me.rhunk.snapenhance.features.impl.downloader

import android.app.AlertDialog
import android.content.DialogInterface
import android.graphics.Bitmap
import android.net.Uri
import android.widget.ImageView
import com.arthenica.ffmpegkit.FFmpegKit
import kotlinx.coroutines.runBlocking
import me.rhunk.snapenhance.Constants.ARROYO_URL_KEY_PROTO_PATH
import me.rhunk.snapenhance.Logger.xposedLog
import me.rhunk.snapenhance.config.ConfigProperty
import me.rhunk.snapenhance.data.ContentType
import me.rhunk.snapenhance.data.FileType
import me.rhunk.snapenhance.data.wrapper.impl.media.MediaInfo
import me.rhunk.snapenhance.data.wrapper.impl.media.dash.LongformVideoPlaylistItem
import me.rhunk.snapenhance.data.wrapper.impl.media.dash.SnapPlaylistItem
import me.rhunk.snapenhance.data.wrapper.impl.media.opera.Layer
import me.rhunk.snapenhance.data.wrapper.impl.media.opera.ParamMap
import me.rhunk.snapenhance.download.ClientDownloadManager
import me.rhunk.snapenhance.download.DownloadMediaType
import me.rhunk.snapenhance.features.Feature
import me.rhunk.snapenhance.features.FeatureLoadParams
import me.rhunk.snapenhance.features.impl.Messaging
import me.rhunk.snapenhance.features.impl.spying.MessageLogger
import me.rhunk.snapenhance.hook.HookAdapter
import me.rhunk.snapenhance.hook.HookStage
import me.rhunk.snapenhance.hook.Hooker
import me.rhunk.snapenhance.util.EncryptionUtils
import me.rhunk.snapenhance.util.MediaDownloaderHelper
import me.rhunk.snapenhance.util.MediaType
import me.rhunk.snapenhance.util.PreviewUtils
import me.rhunk.snapenhance.util.getObjectField
import me.rhunk.snapenhance.util.protobuf.ProtoReader
import java.io.ByteArrayInputStream
import java.io.File
import java.nio.file.Paths
import java.text.SimpleDateFormat
import java.util.Locale
import kotlin.coroutines.suspendCoroutine
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlin.io.path.inputStream

@OptIn(ExperimentalEncodingApi::class)
class MediaDownloader : Feature("MediaDownloader", loadParams = FeatureLoadParams.ACTIVITY_CREATE_ASYNC) {
    private var lastSeenMediaInfoMap: MutableMap<MediaType, MediaInfo>? = null
    private var lastSeenMapParams: ParamMap? = null
    private val isFFmpegPresent by lazy {
        runCatching { FFmpegKit.execute("-version") }.isSuccess
    }

    private fun provideClientDownloadManager(pathPrefix: String): ClientDownloadManager {
        return ClientDownloadManager(
            context,
            File(
                context.config.string(ConfigProperty.SAVE_FOLDER),
                createNewFilePath(pathPrefix.hashCode(), pathPrefix)
            ).absolutePath
        )
    }

    private fun canMergeOverlay(): Boolean {
        if (context.config.options(ConfigProperty.DOWNLOAD_OPTIONS)["merge_overlay"] == false) return false
        return isFFmpegPresent
    }

    private fun createNewFilePath(hash: Int, pathPrefix: String): String {
        val hexHash = Integer.toHexString(hash)
        val downloadOptions = context.config.options(ConfigProperty.DOWNLOAD_OPTIONS)

        val currentDateTime = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.ENGLISH).format(System.currentTimeMillis())

        val finalPath = StringBuilder()

        fun appendFileName(string: String) {
            if (finalPath.isEmpty() || finalPath.endsWith("/")) {
                finalPath.append(string)
            } else {
                finalPath.append("_").append(string)
            }
        }

        if (downloadOptions["format_user_folder"] == true) {
            finalPath.append(pathPrefix).append("/")
        }
        if (downloadOptions["format_hash"] == true) {
            appendFileName(hexHash)
        }
        if (downloadOptions["format_username"] == true) {
            appendFileName(pathPrefix)
        }
        if (downloadOptions["format_date_time"] == true) {
            appendFileName(currentDateTime)
        }

        if (finalPath.isEmpty()) finalPath.append(hexHash)

        return finalPath.toString()
    }

    /*
     * Download the last seen media
     */
    fun downloadLastOperaMediaAsync() {
        if (lastSeenMapParams == null || lastSeenMediaInfoMap == null) return
        context.executeAsync {
            handleOperaMedia(lastSeenMapParams!!, lastSeenMediaInfoMap!!, true)
        }
    }

    private fun handleLocalReferences(path: String) = runBlocking {
        //if the media is a local file we need to copy it to a public directory
        //so the broadcast receiver can access it
        return@runBlocking Uri.parse(path).let { uri ->
            if (uri.scheme == "file") {
                return@let suspendCoroutine<String> { continuation ->
                    context.downloadServer.ensureServerStarted {
                        val url = putDownloadableContent(Paths.get(uri.path).inputStream())
                        continuation.resumeWith(Result.success(url))
                    }
                }
            }
            path
        }
    }

    private fun downloadOperaMedia(mediaInfoMap: Map<MediaType, MediaInfo>, author: String) {
        if (mediaInfoMap.isEmpty()) return

        val originalMediaInfo = mediaInfoMap[MediaType.ORIGINAL]!!
        val overlay = mediaInfoMap[MediaType.OVERLAY]

        val originalMediaInfoReference = handleLocalReferences(originalMediaInfo.uri)
        val overlayReference = overlay?.let { handleLocalReferences(it.uri) }

        val clientDownloadManager = provideClientDownloadManager(author)

        overlay?.let {
            clientDownloadManager.downloadMediaWithOverlay(
                originalMediaInfoReference,
                overlayReference!!,
                DownloadMediaType.fromUri(Uri.parse(originalMediaInfoReference)),
                DownloadMediaType.fromUri(Uri.parse(overlayReference)),
                videoEncryption = originalMediaInfo.encryption?.let {
                    Pair(Base64.UrlSafe.encode(it.keySpec), Base64.UrlSafe.encode(it.ivKeyParameterSpec))
                },
                overlayEncryption = overlay.encryption?.let {
                    Pair(Base64.UrlSafe.encode(it.keySpec), Base64.UrlSafe.encode(it.ivKeyParameterSpec))
                }
            )
            return
        }

        clientDownloadManager.downloadMedia(
            originalMediaInfoReference,
            DownloadMediaType.fromUri(Uri.parse(originalMediaInfoReference)),
            originalMediaInfo.encryption?.let {
                Pair(Base64.UrlSafe.encode(it.keySpec), Base64.UrlSafe.encode(it.ivKeyParameterSpec))
            }
        )
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
        paramMap["MESSAGE_ID"]?.toString()?.takeIf { forceDownload || canAutoDownload("friend_snaps") }?.let { id ->
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
        paramMap["PLAYLIST_V2_GROUP"]?.toString()?.takeIf {
            it.contains("storyUserId=") && (forceDownload || canAutoDownload("friend_stories"))
        }?.let { playlistGroup ->
            val storyIdStartIndex = playlistGroup.indexOf("storyUserId=") + 12
            val storyUserId = playlistGroup.substring(
                storyIdStartIndex,
                playlistGroup.indexOf(",", storyIdStartIndex)
            )
            val author = context.database.getFriendInfo(if (storyUserId == "null") context.database.getMyUserId()!! else storyUserId)

            downloadOperaMedia(mediaInfoMap, author!!.usernameForSorting!!)
            return
        }

        val snapSource = paramMap["SNAP_SOURCE"].toString()

        //public stories
        if ((snapSource == "PUBLIC_USER" || snapSource == "SAVED_STORY") &&
            (forceDownload || canAutoDownload("public_stories"))) {
            val userDisplayName = (if (paramMap.containsKey("USER_DISPLAY_NAME")) paramMap["USER_DISPLAY_NAME"].toString() else "").replace(
                    "[^\\x00-\\x7F]".toRegex(),
                    "")
            downloadOperaMedia(mediaInfoMap, "Public-Stories/$userDisplayName")
            return
        }

        //spotlight
        if (snapSource == "SINGLE_SNAP_STORY" && (forceDownload || canAutoDownload("spotlight"))) {
            downloadOperaMedia(mediaInfoMap, "Spotlight")
            return
        }

        //stories with mpeg dash media
        //TODO: option to download multiple chapters
        if (paramMap.containsKey("LONGFORM_VIDEO_PLAYLIST_ITEM") && forceDownload) {
            if (!isFFmpegPresent) {
                context.shortToast("Can't download media. ffmpeg was not found")
                return
            }

            val storyName = paramMap["STORY_NAME"].toString().replace(
                "[^\\x00-\\x7F]".toRegex(),
                "")

            //get the position of the media in the playlist and the duration
            val snapItem = SnapPlaylistItem(paramMap["SNAP_PLAYLIST_ITEM"]!!)
            val snapChapterList = LongformVideoPlaylistItem(paramMap["LONGFORM_VIDEO_PLAYLIST_ITEM"]!!).chapters
            if (snapChapterList.isEmpty()) {
                context.shortToast("No chapters found")
                return
            }
            val snapChapter = snapChapterList.first { it.snapId == snapItem.snapId }
            val nextChapter = snapChapterList.getOrNull(snapChapterList.indexOf(snapChapter) + 1)

            //add 100ms to the start time to prevent the video from starting too early
            val snapChapterTimestamp = snapChapter.startTimeMs.plus(100)
            val duration = nextChapter?.startTimeMs?.minus(snapChapterTimestamp) ?: 0

            //get the mpd playlist and append the cdn url to baseurl nodes
            val playlistUrl = paramMap["MEDIA_ID"].toString().let { it.substring(it.indexOf("https://cf-st.sc-cdn.net")) }
            val clientDownloadManager = provideClientDownloadManager("Pro-Stories/${storyName}")
            clientDownloadManager.downloadDashMedia(
                playlistUrl,
                snapChapterTimestamp,
                duration
            )
        }
    }

    private fun canAutoDownload(keyFilter: String? = null): Boolean {
        val options = context.config.options(ConfigProperty.AUTO_DOWNLOAD_OPTIONS)
        return options.filter { it.value }.any { keyFilter == null || it.key.contains(keyFilter, true) }
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
        val urlProto: ByteArray = messageReader.getByteArray(*ARROYO_URL_KEY_PROTO_PATH)!!

        //download the message content
        try {
            val downloadedMedia = MediaDownloaderHelper.downloadMediaFromReference(urlProto, canMergeOverlay(), isPreviewMode) {
                EncryptionUtils.decryptInputStreamFromArroyo(it, contentType, messageReader)
            }[MediaType.ORIGINAL] ?: throw Exception("Failed to download media")
            val fileType = FileType.fromByteArray(downloadedMedia)

            if (isPreviewMode) {
                runCatching {
                    val bitmap: Bitmap? = PreviewUtils.createPreview(downloadedMedia, fileType.isVideo)
                    if (bitmap == null) {
                        context.shortToast("Failed to create preview")
                        return
                    }
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
                    context.shortToast("Failed to create preview: $it")
                    xposedLog(it)
                }
                return
            }

            //TODO: use input streams instead of bytearrays
            context.downloadServer.ensureServerStarted {
                val requestUrl = putDownloadableContent(ByteArrayInputStream(downloadedMedia))
                provideClientDownloadManager(messageAuthor).downloadMedia(
                    requestUrl,
                    DownloadMediaType.REMOTE_MEDIA
                )
            }

        } catch (e: Throwable) {
            context.longToast("Failed to download " + e.message)
            xposedLog(e)
        }
    }
}
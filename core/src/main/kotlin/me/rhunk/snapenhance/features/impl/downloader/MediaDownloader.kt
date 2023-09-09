package me.rhunk.snapenhance.features.impl.downloader

import android.content.DialogInterface
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.widget.ImageView
import kotlinx.coroutines.runBlocking
import me.rhunk.snapenhance.bridge.DownloadCallback
import me.rhunk.snapenhance.core.database.objects.FriendInfo
import me.rhunk.snapenhance.core.download.DownloadManagerClient
import me.rhunk.snapenhance.core.download.data.DownloadMediaType
import me.rhunk.snapenhance.core.download.data.DownloadMetadata
import me.rhunk.snapenhance.core.download.data.InputMedia
import me.rhunk.snapenhance.core.download.data.MediaDownloadSource
import me.rhunk.snapenhance.core.download.data.SplitMediaAssetType
import me.rhunk.snapenhance.core.download.data.toKeyPair
import me.rhunk.snapenhance.core.messaging.MessagingRuleType
import me.rhunk.snapenhance.core.util.download.RemoteMediaResolver
import me.rhunk.snapenhance.core.util.ktx.getObjectField
import me.rhunk.snapenhance.core.util.protobuf.ProtoReader
import me.rhunk.snapenhance.core.util.snap.BitmojiSelfie
import me.rhunk.snapenhance.core.util.snap.EncryptionHelper
import me.rhunk.snapenhance.core.util.snap.MediaDownloaderHelper
import me.rhunk.snapenhance.core.util.snap.PreviewUtils
import me.rhunk.snapenhance.data.ContentType
import me.rhunk.snapenhance.data.FileType
import me.rhunk.snapenhance.data.wrapper.impl.media.MediaInfo
import me.rhunk.snapenhance.data.wrapper.impl.media.dash.LongformVideoPlaylistItem
import me.rhunk.snapenhance.data.wrapper.impl.media.dash.SnapPlaylistItem
import me.rhunk.snapenhance.data.wrapper.impl.media.opera.Layer
import me.rhunk.snapenhance.data.wrapper.impl.media.opera.ParamMap
import me.rhunk.snapenhance.features.FeatureLoadParams
import me.rhunk.snapenhance.features.MessagingRuleFeature
import me.rhunk.snapenhance.features.impl.Messaging
import me.rhunk.snapenhance.features.impl.spying.MessageLogger
import me.rhunk.snapenhance.hook.HookAdapter
import me.rhunk.snapenhance.hook.HookStage
import me.rhunk.snapenhance.hook.Hooker
import me.rhunk.snapenhance.ui.ViewAppearanceHelper
import java.nio.file.Paths
import java.text.SimpleDateFormat
import java.util.Locale
import kotlin.coroutines.suspendCoroutine
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

private fun String.sanitizeForPath(): String {
    return this.replace(" ", "_")
        .replace(Regex("\\p{Cntrl}"), "")
}

class SnapChapterInfo(
    val offset: Long,
    val duration: Long?
)


@OptIn(ExperimentalEncodingApi::class)
class MediaDownloader : MessagingRuleFeature("MediaDownloader", MessagingRuleType.AUTO_DOWNLOAD, loadParams = FeatureLoadParams.ACTIVITY_CREATE_ASYNC) {
    private var lastSeenMediaInfoMap: MutableMap<SplitMediaAssetType, MediaInfo>? = null
    private var lastSeenMapParams: ParamMap? = null

    private fun provideDownloadManagerClient(
        mediaIdentifier: String,
        mediaAuthor: String,
        downloadSource: MediaDownloadSource,
        friendInfo: FriendInfo? = null
    ): DownloadManagerClient {
        val generatedHash = mediaIdentifier.hashCode().toString(16).replaceFirst("-", "")

        val iconUrl = BitmojiSelfie.getBitmojiSelfie(friendInfo?.bitmojiSelfieId, friendInfo?.bitmojiAvatarId, BitmojiSelfie.BitmojiSelfieType.THREE_D)

        val downloadLogging by context.config.downloader.logging
        if (downloadLogging.contains("started")) {
            context.shortToast(context.translation["download_processor.download_started_toast"])
        }

        val outputPath = createNewFilePath(generatedHash, downloadSource, mediaAuthor)

        return DownloadManagerClient(
            context = context,
            metadata = DownloadMetadata(
                mediaIdentifier = if (!context.config.downloader.allowDuplicate.get()) {
                    generatedHash
                } else null,
                mediaAuthor = mediaAuthor,
                downloadSource = downloadSource.key,
                iconUrl = iconUrl,
                outputPath = outputPath
            ),
            callback = object: DownloadCallback.Stub() {
                override fun onSuccess(outputFile: String) {
                    if (!downloadLogging.contains("success")) return
                    context.log.verbose("onSuccess: outputFile=$outputFile")
                    context.shortToast(context.translation.format("download_processor.saved_toast", "path" to outputFile.split("/").takeLast(2).joinToString("/")))
                }

                override fun onProgress(message: String) {
                    if (!downloadLogging.contains("progress")) return
                    context.log.verbose("onProgress: message=$message")
                    context.shortToast(message)
                }

                override fun onFailure(message: String, throwable: String?) {
                    if (!downloadLogging.contains("failure")) return
                    context.log.verbose("onFailure: message=$message, throwable=$throwable")
                    throwable?.let {
                        context.longToast((message + it.takeIf { it.isNotEmpty() }.orEmpty()))
                        return
                    }
                    context.shortToast(message)
                }
            }
        )
    }


    private fun createNewFilePath(hexHash: String, downloadSource: MediaDownloadSource, mediaAuthor: String): String {
        val pathFormat by context.config.downloader.pathFormat
        val sanitizedMediaAuthor = mediaAuthor.sanitizeForPath().ifEmpty { hexHash }

        val currentDateTime = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.ENGLISH).format(System.currentTimeMillis())

        val finalPath = StringBuilder()

        fun appendFileName(string: String) {
            if (finalPath.isEmpty() || finalPath.endsWith("/")) {
                finalPath.append(string)
            } else {
                finalPath.append("_").append(string)
            }
        }

        if (pathFormat.contains("create_author_folder")) {
            finalPath.append(sanitizedMediaAuthor).append("/")
        }
        if (pathFormat.contains("create_source_folder")) {
            finalPath.append(downloadSource.pathName).append("/")
        }
        if (pathFormat.contains("append_hash")) {
            appendFileName(hexHash)
        }
        if (pathFormat.contains("append_source")) {
            appendFileName(downloadSource.pathName)
        }
        if (pathFormat.contains("append_username")) {
            appendFileName(sanitizedMediaAuthor)
        }
        if (pathFormat.contains("append_date_time")) {
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
        Uri.parse(path).let { uri ->
            if (uri.scheme == "file") {
                return@let suspendCoroutine<String> { continuation ->
                    context.httpServer.ensureServerStarted {
                        val file = Paths.get(uri.path).toFile()
                        val url = putDownloadableContent(file.inputStream(), file.length())
                        continuation.resumeWith(Result.success(url))
                    }
                }
            }
            path
        }
    }

    private fun downloadOperaMedia(downloadManagerClient: DownloadManagerClient, mediaInfoMap: Map<SplitMediaAssetType, MediaInfo>) {
        if (mediaInfoMap.isEmpty()) return

        val originalMediaInfo = mediaInfoMap[SplitMediaAssetType.ORIGINAL]!!
        val originalMediaInfoReference = handleLocalReferences(originalMediaInfo.uri)

        mediaInfoMap[SplitMediaAssetType.OVERLAY]?.let { overlay ->
            val overlayReference = handleLocalReferences(overlay.uri)

            downloadManagerClient.downloadMediaWithOverlay(
                original = InputMedia(
                    originalMediaInfoReference,
                    DownloadMediaType.fromUri(Uri.parse(originalMediaInfoReference)),
                    originalMediaInfo.encryption?.toKeyPair()
                ),
                overlay = InputMedia(
                    overlayReference,
                    DownloadMediaType.fromUri(Uri.parse(overlayReference)),
                    overlay.encryption?.toKeyPair(),
                    isOverlay = true
                )
            )
            return
        }

        downloadManagerClient.downloadSingleMedia(
            originalMediaInfoReference,
            DownloadMediaType.fromUri(Uri.parse(originalMediaInfoReference)),
            originalMediaInfo.encryption?.toKeyPair()
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
        mediaInfoMap: Map<SplitMediaAssetType, MediaInfo>,
        forceDownload: Boolean
    ) {
        //messages
        paramMap["MESSAGE_ID"]?.toString()?.takeIf { forceDownload || canAutoDownload("friend_snaps") }?.let { id ->
            val messageId = id.substring(id.lastIndexOf(":") + 1).toLong()
            val conversationMessage = context.database.getConversationMessageFromId(messageId)!!

            val senderId = conversationMessage.senderId!!
            val conversationId = conversationMessage.clientConversationId!!

            if (!forceDownload && !canUseRule(senderId)) {
                return
            }

            if (!forceDownload && context.config.downloader.preventSelfAutoDownload.get() && senderId == context.database.myUserId) return

            val author = context.database.getFriendInfo(senderId) ?: return
            val authorUsername = author.usernameForSorting!!

            downloadOperaMedia(provideDownloadManagerClient(
                mediaIdentifier = "$conversationId$senderId${conversationMessage.serverMessageId}",
                mediaAuthor = authorUsername,
                downloadSource = MediaDownloadSource.CHAT_MEDIA,
                friendInfo = author
            ), mediaInfoMap)

            return
        }

        //private stories
        paramMap["PLAYLIST_V2_GROUP"]?.takeIf {
            forceDownload || canAutoDownload("friend_stories")
        }?.let { playlistGroup ->
            val playlistGroupString = playlistGroup.toString()

            val storyUserId = paramMap["TOPIC_SNAP_CREATOR_USER_ID"]?.toString() ?: if (playlistGroupString.contains("storyUserId=")) {
                (playlistGroupString.indexOf("storyUserId=") + 12).let {
                    playlistGroupString.substring(it, playlistGroupString.indexOf(",", it))
                }
            } else {
                //story replies
                val arroyoMessageId = playlistGroup::class.java.methods.firstOrNull { it.name == "getId" }
                    ?.invoke(playlistGroup)?.toString()
                    ?.split(":")?.getOrNull(2) ?: return@let

                val conversationMessage = context.database.getConversationMessageFromId(arroyoMessageId.toLong()) ?: return@let
                val conversationParticipants = context.database.getConversationParticipants(conversationMessage.clientConversationId.toString()) ?: return@let

                conversationParticipants.firstOrNull { it != conversationMessage.senderId }
            }

            val author = context.database.getFriendInfo(
                if (storyUserId == null || storyUserId == "null")
                    context.database.myUserId
                else storyUserId
            ) ?: throw Exception("Friend not found in database")
            val authorName = author.usernameForSorting!!

            if (!forceDownload) {
                if (context.config.downloader.preventSelfAutoDownload.get() && author.userId == context.database.myUserId) return
                if (!canUseRule(author.userId!!)) return
            }

            downloadOperaMedia(provideDownloadManagerClient(
                mediaIdentifier = paramMap["MEDIA_ID"].toString(),
                mediaAuthor = authorName,
                downloadSource = MediaDownloadSource.STORY,
                friendInfo = author
            ), mediaInfoMap)
            return
        }

        val snapSource = paramMap["SNAP_SOURCE"].toString()

        //public stories
        if ((snapSource == "PUBLIC_USER" || snapSource == "SAVED_STORY") &&
            (forceDownload || canAutoDownload("public_stories"))) {
            val userDisplayName = (if (paramMap.containsKey("USER_DISPLAY_NAME")) paramMap["USER_DISPLAY_NAME"].toString() else "").sanitizeForPath()

            downloadOperaMedia(provideDownloadManagerClient(
                mediaIdentifier = paramMap["SNAP_ID"].toString(),
                mediaAuthor = userDisplayName,
                downloadSource = MediaDownloadSource.PUBLIC_STORY,
            ), mediaInfoMap)
            return
        }

        //spotlight
        if (snapSource == "SINGLE_SNAP_STORY" && (forceDownload || canAutoDownload("spotlight"))) {
            downloadOperaMedia(provideDownloadManagerClient(
                mediaIdentifier = paramMap["SNAP_ID"].toString(),
                downloadSource = MediaDownloadSource.SPOTLIGHT,
                mediaAuthor = paramMap["TIME_STAMP"].toString()
            ), mediaInfoMap)
            return
        }

        //stories with mpeg dash media
        if (paramMap.containsKey("LONGFORM_VIDEO_PLAYLIST_ITEM") && forceDownload) {
            val storyName = paramMap["STORY_NAME"].toString().sanitizeForPath()
            //get the position of the media in the playlist and the duration
            val snapItem = SnapPlaylistItem(paramMap["SNAP_PLAYLIST_ITEM"]!!)
            val snapChapterList = LongformVideoPlaylistItem(paramMap["LONGFORM_VIDEO_PLAYLIST_ITEM"]!!).chapters
            val currentChapterIndex = snapChapterList.indexOfFirst { it.snapId == snapItem.snapId }

            if (snapChapterList.isEmpty()) {
                context.shortToast("No chapters found")
                return
            }

            fun prettyPrintTime(time: Long): String {
                val seconds = time / 1000
                val minutes = seconds / 60
                val hours = minutes / 60
                return "${hours % 24}:${minutes % 60}:${seconds % 60}"
            }

            val playlistUrl = paramMap["MEDIA_ID"].toString().let {
                val urlIndexes = arrayOf(it.indexOf("https://cf-st.sc-cdn.net"), it.indexOf("https://bolt-gcdn.sc-cdn.net"))

                urlIndexes.firstOrNull { index -> index != -1 }?.let { validIndex ->
                    it.substring(validIndex)
                } ?: "${RemoteMediaResolver.CF_ST_CDN_D}$it"
            }

            context.runOnUiThread {
                val selectedChapters = mutableListOf<Int>()
                val chapters = snapChapterList.mapIndexed { index, snapChapter ->
                    val nextChapter = snapChapterList.getOrNull(index + 1)
                    val duration = nextChapter?.startTimeMs?.minus(snapChapter.startTimeMs)
                    SnapChapterInfo(snapChapter.startTimeMs, duration)
                }
                ViewAppearanceHelper.newAlertDialogBuilder(context.mainActivity!!).apply {
                    setTitle("Download dash media")
                    setMultiChoiceItems(
                        chapters.map { "Segment ${prettyPrintTime(it.offset)} - ${prettyPrintTime(it.offset + (it.duration ?: 0))}" }.toTypedArray(),
                        List(chapters.size) { index -> currentChapterIndex == index }.toBooleanArray()
                    ) { _, which, isChecked ->
                        if (isChecked) {
                            selectedChapters.add(which)
                        } else if (selectedChapters.contains(which)) {
                            selectedChapters.remove(which)
                        }
                    }
                    setNegativeButton("Cancel") { dialog, _ -> dialog.dismiss() }
                    setNeutralButton("Download all") { _, _ ->
                        provideDownloadManagerClient(
                            mediaIdentifier = paramMap["STORY_ID"].toString(),
                            downloadSource = MediaDownloadSource.PUBLIC_STORY,
                            mediaAuthor = storyName
                        ).downloadDashMedia(playlistUrl, 0, null)
                    }
                    setPositiveButton("Download") { _, _ ->
                        val groups = mutableListOf<MutableList<SnapChapterInfo>>()
                        var currentGroup = mutableListOf<SnapChapterInfo>()
                        var lastChapterIndex = -1

                        //check for consecutive chapters
                        chapters.filterIndexed { index, _ -> selectedChapters.contains(index) }
                            .forEachIndexed { index, pair ->
                                if (lastChapterIndex != -1 && index != lastChapterIndex + 1) {
                                    groups.add(currentGroup)
                                    currentGroup = mutableListOf()
                                }
                                currentGroup.add(pair)
                                lastChapterIndex = index
                        }

                        if (currentGroup.isNotEmpty()) {
                            groups.add(currentGroup)
                        }

                        groups.forEach { group ->
                            val firstChapter = group.first()
                            val lastChapter = group.last()
                            val duration = if (firstChapter == lastChapter) {
                                firstChapter.duration
                            } else {
                                lastChapter.duration?.let { lastChapter.offset - firstChapter.offset + it }
                            }

                            provideDownloadManagerClient(
                                mediaIdentifier = "${paramMap["STORY_ID"]}-${firstChapter.offset}-${lastChapter.offset}",
                                downloadSource = MediaDownloadSource.PUBLIC_STORY,
                                mediaAuthor = storyName
                            ).downloadDashMedia(
                                playlistUrl,
                                firstChapter.offset.plus(100),
                                duration
                            )
                        }
                    }
                }.show()
            }
        }
    }

    private fun canAutoDownload(keyFilter: String? = null): Boolean {
        val options by context.config.downloader.autoDownloadSources
        return options.any { keyFilter == null || it.contains(keyFilter, true) }
    }

    override fun asyncOnActivityCreate() {
        val operaViewerControllerClass: Class<*> = context.mappings.getMappedClass("OperaPageViewController", "class")

        val onOperaViewStateCallback: (HookAdapter) -> Unit = onOperaViewStateCallback@{ param ->

            val viewState = (param.thisObject() as Any).getObjectField(context.mappings.getMappedValue("OperaPageViewController", "viewStateField")).toString()
            if (viewState != "FULLY_DISPLAYED") {
                return@onOperaViewStateCallback
            }
            val operaLayerList = (param.thisObject() as Any).getObjectField(context.mappings.getMappedValue("OperaPageViewController", "layerListField")) as ArrayList<*>
            val mediaParamMap: ParamMap = operaLayerList.map { Layer(it) }.first().paramMap

            if (!mediaParamMap.containsKey("image_media_info") && !mediaParamMap.containsKey("video_media_info_list"))
                return@onOperaViewStateCallback

            val mediaInfoMap = mutableMapOf<SplitMediaAssetType, MediaInfo>()
            val isVideo = mediaParamMap.containsKey("video_media_info_list")

            mediaInfoMap[SplitMediaAssetType.ORIGINAL] = MediaInfo(
                (if (isVideo) mediaParamMap["video_media_info_list"] else mediaParamMap["image_media_info"])!!
            )

            if (context.config.downloader.mergeOverlays.get() && mediaParamMap.containsKey("overlay_image_media_info")) {
                mediaInfoMap[SplitMediaAssetType.OVERLAY] =
                    MediaInfo(mediaParamMap["overlay_image_media_info"]!!)
            }
            lastSeenMapParams = mediaParamMap
            lastSeenMediaInfoMap = mediaInfoMap

            if (!canAutoDownload()) return@onOperaViewStateCallback

            context.executeAsync {
                try {
                    handleOperaMedia(mediaParamMap, mediaInfoMap, false)
                } catch (e: Throwable) {
                    context.log.error("Failed to handle opera media", e)
                    context.longToast(e.message)
                }
            }
        }

        arrayOf("onDisplayStateChange", "onDisplayStateChangeGesture").forEach { methodName ->
            Hooker.hook(
                operaViewerControllerClass,
                context.mappings.getMappedValue("OperaPageViewController", methodName),
                HookStage.AFTER, onOperaViewStateCallback
            )
        }
    }

    fun downloadMessageId(messageId: Long, isPreview: Boolean = false) {
        val messageLogger = context.feature(MessageLogger::class)
        val message = context.database.getConversationMessageFromId(messageId) ?: throw Exception("Message not found in database")

        //get the message author
        val friendInfo: FriendInfo = context.database.getFriendInfo(message.senderId!!) ?: throw Exception("Friend not found in database")
        val authorName = friendInfo.usernameForSorting!!

        var messageContent = message.messageContent!!
        var isArroyoMessage = true
        var deletedMediaReference: ByteArray? = null

        //check if the messageId
        var contentType: ContentType = ContentType.fromId(message.contentType)

        if (messageLogger.isMessageRemoved(message.clientConversationId!!, message.serverMessageId.toLong())) {
            val messageObject = messageLogger.getMessageObject(message.clientConversationId!!, message.serverMessageId.toLong()) ?: throw Exception("Message not found in database")
            isArroyoMessage = false
            val messageContentObject = messageObject.getAsJsonObject("mMessageContent")

            messageContent = messageContentObject
                .getAsJsonArray("mContent")
                .map { it.asByte }
                .toByteArray()

            contentType = ContentType.valueOf(messageContentObject
                .getAsJsonPrimitive("mContentType").asString
            )

            deletedMediaReference = messageContentObject.getAsJsonArray("mRemoteMediaReferences")
                .map { it.asJsonObject.getAsJsonArray("mMediaReferences") }
                .flatten().let {  reference ->
                    if (reference.isEmpty()) return@let null
                    reference[0].asJsonObject.getAsJsonArray("mContentObject").map { it.asByte }.toByteArray()
                }
        }

        val translations = context.translation.getCategory("download_processor")

        if (contentType != ContentType.NOTE &&
            contentType != ContentType.SNAP &&
            contentType != ContentType.EXTERNAL_MEDIA) {
            context.shortToast(translations["unsupported_content_type_toast"])
            return
        }

        val messageReader = ProtoReader(messageContent)
        val urlProto: ByteArray? = if (isArroyoMessage) {
            var finalProto: ByteArray? = null
            messageReader.eachBuffer(4, 5) {
                finalProto = getByteArray(1, 3)
            }
            finalProto
        } else deletedMediaReference

        if (urlProto == null) {
            context.shortToast(translations["unsupported_content_type_toast"])
            return
        }

        runCatching {
            if (!isPreview) {
                val encryptionKeys = EncryptionHelper.getEncryptionKeys(contentType, messageReader, isArroyo = isArroyoMessage)
                provideDownloadManagerClient(
                    mediaIdentifier = "${message.clientConversationId}${message.senderId}${message.serverMessageId}",
                    downloadSource = MediaDownloadSource.CHAT_MEDIA,
                    mediaAuthor = authorName,
                    friendInfo = friendInfo
                ).downloadSingleMedia(
                    mediaData = Base64.UrlSafe.encode(urlProto),
                    mediaType = DownloadMediaType.PROTO_MEDIA,
                    encryption = encryptionKeys?.toKeyPair(),
                    messageContentType = contentType
                )
                return
            }

            if (EncryptionHelper.getEncryptionKeys(contentType, messageReader, isArroyo = isArroyoMessage) == null) {
                context.shortToast(translations["failed_no_longer_available_toast"])
                return
            }

            val downloadedMediaList = MediaDownloaderHelper.downloadMediaFromReference(urlProto) {
                EncryptionHelper.decryptInputStream(it, contentType, messageReader, isArroyo = isArroyoMessage)
            }

            runCatching {
                val originalMedia = downloadedMediaList[SplitMediaAssetType.ORIGINAL] ?: return
                val overlay = downloadedMediaList[SplitMediaAssetType.OVERLAY]

                var bitmap: Bitmap? = PreviewUtils.createPreview(originalMedia, isVideo = FileType.fromByteArray(originalMedia).isVideo)

                if (bitmap == null) {
                    context.shortToast(translations["failed_to_create_preview_toast"])
                    return
                }

                overlay?.let {
                    bitmap = PreviewUtils.mergeBitmapOverlay(bitmap!!, BitmapFactory.decodeByteArray(it, 0, it.size))
                }

                with(ViewAppearanceHelper.newAlertDialogBuilder(context.mainActivity)) {
                    setView(ImageView(context).apply {
                        setImageBitmap(bitmap)
                    })
                    setPositiveButton("Close") { dialog: DialogInterface, _: Int -> dialog.dismiss() }
                    this@MediaDownloader.context.runOnUiThread { show()}
                }
            }.onFailure {
                context.shortToast(translations["failed_to_create_preview_toast"])
                context.log.error("Failed to create preview", it)
            }
        }.onFailure {
            context.longToast(translations["failed_generic_toast"])
            context.log.error("Failed to download message", it)
        }
    }

    fun downloadProfilePicture(url: String, author: String) {
        provideDownloadManagerClient(
            mediaIdentifier = url.hashCode().toString(16).replaceFirst("-", ""),
            mediaAuthor = author,
            downloadSource = MediaDownloadSource.PROFILE_PICTURE
        ).downloadSingleMedia(
            url,
            DownloadMediaType.REMOTE_MEDIA
        )
    }

    /**
     * Called when a message is focused in chat
     */
    fun onMessageActionMenu(isPreviewMode: Boolean) {
        val messaging = context.feature(Messaging::class)
        if (messaging.openedConversationUUID == null) return
        downloadMessageId(messaging.lastFocusedMessageId, isPreviewMode)
    }
}

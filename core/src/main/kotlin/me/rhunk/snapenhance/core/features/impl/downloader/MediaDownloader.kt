package me.rhunk.snapenhance.core.features.impl.downloader

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.view.Gravity
import android.view.ViewGroup.MarginLayoutParams
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import me.rhunk.snapenhance.bridge.DownloadCallback
import me.rhunk.snapenhance.common.data.FileType
import me.rhunk.snapenhance.common.data.MessagingRuleType
import me.rhunk.snapenhance.common.data.download.*
import me.rhunk.snapenhance.common.database.impl.ConversationMessage
import me.rhunk.snapenhance.common.database.impl.FriendInfo
import me.rhunk.snapenhance.common.util.ktx.longHashCode
import me.rhunk.snapenhance.common.util.protobuf.ProtoReader
import me.rhunk.snapenhance.common.util.snap.BitmojiSelfie
import me.rhunk.snapenhance.common.util.snap.MediaDownloaderHelper
import me.rhunk.snapenhance.common.util.snap.RemoteMediaResolver
import me.rhunk.snapenhance.core.DownloadManagerClient
import me.rhunk.snapenhance.core.SnapEnhance
import me.rhunk.snapenhance.core.features.FeatureLoadParams
import me.rhunk.snapenhance.core.features.MessagingRuleFeature
import me.rhunk.snapenhance.core.features.impl.downloader.decoder.DecodedAttachment
import me.rhunk.snapenhance.core.features.impl.downloader.decoder.MessageDecoder
import me.rhunk.snapenhance.core.features.impl.messaging.Messaging
import me.rhunk.snapenhance.core.features.impl.spying.MessageLogger
import me.rhunk.snapenhance.core.ui.ViewAppearanceHelper
import me.rhunk.snapenhance.core.ui.debugEditText
import me.rhunk.snapenhance.core.util.hook.HookAdapter
import me.rhunk.snapenhance.core.util.hook.HookStage
import me.rhunk.snapenhance.core.util.hook.hook
import me.rhunk.snapenhance.core.util.ktx.getObjectField
import me.rhunk.snapenhance.core.util.media.PreviewUtils
import me.rhunk.snapenhance.core.wrapper.impl.SnapUUID
import me.rhunk.snapenhance.core.wrapper.impl.media.MediaInfo
import me.rhunk.snapenhance.core.wrapper.impl.media.dash.LongformVideoPlaylistItem
import me.rhunk.snapenhance.core.wrapper.impl.media.dash.SnapPlaylistItem
import me.rhunk.snapenhance.core.wrapper.impl.media.opera.Layer
import me.rhunk.snapenhance.core.wrapper.impl.media.opera.ParamMap
import me.rhunk.snapenhance.core.wrapper.impl.media.toKeyPair
import me.rhunk.snapenhance.mapper.impl.OperaPageViewControllerMapper
import java.io.ByteArrayInputStream
import java.nio.file.Paths
import java.util.UUID
import kotlin.coroutines.suspendCoroutine
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlin.math.absoluteValue

class SnapChapterInfo(
    val offset: Long,
    val duration: Long?
)


@OptIn(ExperimentalEncodingApi::class)
class MediaDownloader : MessagingRuleFeature("MediaDownloader", MessagingRuleType.AUTO_DOWNLOAD, loadParams = FeatureLoadParams.ACTIVITY_CREATE_ASYNC) {
    private var lastSeenMediaInfoMap: MutableMap<SplitMediaAssetType, MediaInfo>? = null
    var lastSeenMapParams: ParamMap? = null
        private set
    private val translations by lazy {
        context.translation.getCategory("download_processor")
    }

    private fun provideDownloadManagerClient(
        mediaIdentifier: String,
        mediaAuthor: String,
        creationTimestamp: Long? = null,
        downloadSource: MediaDownloadSource,
        friendInfo: FriendInfo? = null
    ): DownloadManagerClient {
        val generatedHash = (
            if (!context.config.downloader.allowDuplicate.get()) mediaIdentifier
            else UUID.randomUUID().toString()
        ).longHashCode().absoluteValue.toString(16)

        val iconUrl = BitmojiSelfie.getBitmojiSelfie(friendInfo?.bitmojiSelfieId, friendInfo?.bitmojiAvatarId, BitmojiSelfie.BitmojiSelfieType.THREE_D)

        val downloadLogging by context.config.downloader.logging
        if (downloadLogging.contains("started")) {
            context.shortToast(translations["download_started_toast"])
        }

        val outputPath = createNewFilePath(
            context.config,
            generatedHash.substring(0, generatedHash.length.coerceAtMost(8)),
            downloadSource,
            mediaAuthor,
            creationTimestamp?.takeIf { it > 0L }
        )

        return DownloadManagerClient(
            context = context,
            metadata = DownloadMetadata(
                mediaIdentifier = generatedHash,
                mediaAuthor = mediaAuthor,
                downloadSource = downloadSource.translate(context.translation),
                iconUrl = iconUrl,
                outputPath = outputPath
            ),
            callback = object: DownloadCallback.Stub() {
                override fun onSuccess(outputFile: String) {
                    if (!downloadLogging.contains("success")) return
                    context.log.verbose("onSuccess: outputFile=$outputFile")
                    context.shortToast(translations.format("saved_toast", "path" to outputFile.split("/").takeLast(2).joinToString("/")))
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

    /*
     * Download the last seen media
     */
    fun downloadLastOperaMediaAsync() {
        if (lastSeenMapParams == null || lastSeenMediaInfoMap == null) return
        context.executeAsync {
            handleOperaMedia(lastSeenMapParams!!, lastSeenMediaInfoMap!!, true)
        }
    }

    fun showLastOperaDebugMediaInfo() {
        if (lastSeenMapParams == null || lastSeenMediaInfoMap == null) return

        context.runOnUiThread {
            val mediaInfoText = lastSeenMapParams?.concurrentHashMap?.map { (key, value) ->
                val transformedValue = value.let {
                    if (it::class.java == SnapEnhance.classCache.snapUUID) {
                        SnapUUID(it).toString()
                    }
                    it
                }
                "- $key: $transformedValue"
            }?.joinToString("\n") ?: "No media info found"

            ViewAppearanceHelper.newAlertDialogBuilder(context.mainActivity!!).apply {
                setTitle("Debug Media Info")
                setView(debugEditText(context, mediaInfoText))
                setNeutralButton("Copy") { _, _ ->
                    this@MediaDownloader.context.copyToClipboard(mediaInfoText)
                }
                setPositiveButton("Download") { _, _ ->
                    downloadLastOperaMediaAsync()
                }
                setNegativeButton("Cancel") { dialog, _ -> dialog.dismiss() }
            }.show()
        }
    }

    private fun handleLocalReferences(path: String) = runBlocking {
        Uri.parse(path).let { uri ->
            if (uri.scheme == "file" || uri.scheme == null) {
                return@let suspendCoroutine<String> { continuation ->
                    context.httpServer.ensureServerStarted()?.let { server ->
                        val file = Paths.get(uri.path).toFile()
                        val url = server.putDownloadableContent(file.inputStream(), file.length())
                        continuation.resumeWith(Result.success(url))
                    } ?: run {
                        continuation.resumeWith(Result.failure(Exception("Failed to start http server")))
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
            val mediaId = paramMap["MEDIA_ID"]?.toString()?.let {
                if (it.contains("-")) it.substringAfter("-")
                else it
            }?.substringBefore(".")

            downloadOperaMedia(provideDownloadManagerClient(
                mediaIdentifier = "$conversationId$senderId${conversationMessage.serverMessageId}$mediaId",
                mediaAuthor = authorUsername,
                creationTimestamp = conversationMessage.creationTimestamp,
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
                playlistGroupString.substringAfter("storyUserId=").substringBefore(",")
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
                creationTimestamp = paramMap["PLAYABLE_STORY_SNAP_RECORD"]?.toString()?.substringAfter("timestamp=")
                    ?.substringBefore(",")?.toLongOrNull(),
                downloadSource = MediaDownloadSource.STORY,
                friendInfo = author
            ), mediaInfoMap)
            return
        }

        val snapSource = paramMap["SNAP_SOURCE"].toString()

        //public stories
        if ((snapSource == "PUBLIC_USER" || snapSource == "SAVED_STORY") &&
            (forceDownload || canAutoDownload("public_stories"))) {

            val author = (
                paramMap["USER_ID"]?.let { context.database.getFriendInfo(it.toString())?.mutableUsername } // only for following users
                ?: paramMap["USERNAME"]?.toString()?.takeIf {
                    it.contains("value=")
                }?.substringAfter("value=")?.substringBefore(")")?.substringBefore(",")
                ?: paramMap["CONTEXT_USER_IDENTITY"]?.toString()?.takeIf {
                    it.contains("username=")
                }?.substringAfter("username=")?.substringBefore(",")
                // fallback display name
                ?: paramMap["USER_DISPLAY_NAME"]?.toString()?.takeIf { it.isNotEmpty() }
                ?: paramMap["TIME_STAMP"]?.toString()
                ?: "unknown"
            ).sanitizeForPath()

            downloadOperaMedia(provideDownloadManagerClient(
                mediaIdentifier = paramMap["SNAP_ID"].toString(),
                mediaAuthor = author,
                downloadSource = MediaDownloadSource.PUBLIC_STORY,
                creationTimestamp = paramMap["SNAP_TIMESTAMP"]?.toString()?.toLongOrNull(),
            ), mediaInfoMap)
            return
        }

        //spotlight
        if (snapSource == "SINGLE_SNAP_STORY" && (forceDownload || canAutoDownload("spotlight"))) {
            downloadOperaMedia(provideDownloadManagerClient(
                mediaIdentifier = paramMap["SNAP_ID"].toString(),
                downloadSource = MediaDownloadSource.SPOTLIGHT,
                mediaAuthor = paramMap["CREATOR_DISPLAY_NAME"].toString(),
                creationTimestamp = paramMap["SNAP_TIMESTAMP"]?.toString()?.toLongOrNull(),
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
                        List(chapters.size) { index ->
                            if (currentChapterIndex == index) {
                                selectedChapters.add(index)
                                true
                            } else false
                        }.toBooleanArray()
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

                        var lastChapterIndex = -1
                        // group consecutive chapters
                        chapters.forEachIndexed { index, snapChapter ->
                            lastChapterIndex = if (selectedChapters.contains(index)) {
                                if (lastChapterIndex == -1) {
                                    groups.add(mutableListOf())
                                }
                                groups.last().add(snapChapter)
                                index
                            } else {
                                -1
                            }
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
        context.mappings.useMapper(OperaPageViewControllerMapper::class) {
            val onOperaViewStateCallback: (HookAdapter) -> Unit = onOperaViewStateCallback@{ param ->
                val viewState = (param.thisObject() as Any).getObjectField(viewStateField.get()!!).toString()
                if (viewState != "FULLY_DISPLAYED") {
                    return@onOperaViewStateCallback
                }
                val operaLayerList = (param.thisObject() as Any).getObjectField(layerListField.get()!!) as ArrayList<*>
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
                    runCatching {
                        handleOperaMedia(mediaParamMap, mediaInfoMap, false)
                    }.onFailure {
                        context.log.error("Failed to handle opera media", it)
                        context.longToast(it.message)
                    }
                }
            }

            arrayOf(onDisplayStateChange, onDisplayStateChangeGesture).forEach { methodName ->
                classReference.get()?.hook(
                    methodName.get() ?: return@forEach,
                    HookStage.AFTER, onOperaViewStateCallback
                )
            }
        }
    }

    private fun downloadMessageAttachments(
        friendInfo: FriendInfo,
        message: ConversationMessage,
        authorName: String,
        attachments: List<DecodedAttachment>
    ) {
        //TODO: stickers
        attachments.forEach { attachment ->
            runCatching {
                provideDownloadManagerClient(
                    mediaIdentifier = "${message.clientConversationId}${message.senderId}${message.serverMessageId}${attachment.mediaUniqueId}",
                    downloadSource = MediaDownloadSource.CHAT_MEDIA,
                    mediaAuthor = authorName,
                    friendInfo = friendInfo
                ).downloadSingleMedia(
                    mediaData = attachment.mediaUrlKey!!,
                    mediaType = DownloadMediaType.PROTO_MEDIA,
                    encryption = attachment.attachmentInfo?.encryption,
                    attachmentType = attachment.type
                )
            }.onFailure {
                context.longToast(translations["failed_generic_toast"])
                context.log.error("Failed to download", it)
            }
        }
    }


    @SuppressLint("SetTextI18n")
    @OptIn(ExperimentalCoroutinesApi::class)
    fun downloadMessageId(messageId: Long, isPreview: Boolean = false) {
        val messageLogger = context.feature(MessageLogger::class)
        val message = context.database.getConversationMessageFromId(messageId) ?: throw Exception("Message not found in database")

        //get the message author
        val friendInfo: FriendInfo = context.database.getFriendInfo(message.senderId!!) ?: throw Exception("Friend not found in database")
        val authorName = friendInfo.usernameForSorting!!

        val decodedAttachments = (messageLogger.takeIf { it.isEnabled }?.getMessageObject(message.clientConversationId!!, message.clientMessageId.toLong())?.let {
            MessageDecoder.decode(it.getAsJsonObject("mMessageContent"))
        } ?: MessageDecoder.decode(
            protoReader = ProtoReader(message.messageContent!!)
        )).toMutableList()

        context.feature(Messaging::class).conversationManager?.takeIf {
            decodedAttachments.isEmpty()
        }?.also { conversationManager ->
            runBlocking {
                suspendCoroutine { continuation ->
                    conversationManager.fetchMessage(message.clientConversationId!!, message.clientMessageId.toLong(), onSuccess = { message ->
                        decodedAttachments.addAll(MessageDecoder.decode(message.messageContent!!))
                        continuation.resumeWith(Result.success(Unit))
                    }, onError = {
                        continuation.resumeWith(Result.success(Unit))
                    })
                }
            }
        }

        if (decodedAttachments.isEmpty()) {
            context.shortToast(translations["no_attachments_toast"])
            return
        }

        if (!isPreview) {
            if (decodedAttachments.size == 1 ||
                context.mainActivity == null // we can't show alert dialogs when it downloads from a notification, so it downloads the first one
            ) {
                downloadMessageAttachments(friendInfo, message, authorName,
                    listOf(decodedAttachments.first())
                )
                return
            }

            runOnUiThread {
                ViewAppearanceHelper.newAlertDialogBuilder(context.mainActivity).apply {
                    val selectedAttachments = mutableListOf<Int>().apply {
                        addAll(decodedAttachments.indices)
                    }
                    setMultiChoiceItems(
                        decodedAttachments.mapIndexed { index, decodedAttachment ->
                            "${index + 1}: ${translations["attachment_type.${decodedAttachment.type.key}"]} ${decodedAttachment.attachmentInfo?.resolution?.let { "(${it.first}x${it.second})" } ?: ""}"
                        }.toTypedArray(),
                        decodedAttachments.map { true }.toBooleanArray()
                    ) { _, which, isChecked ->
                        if (isChecked) {
                            selectedAttachments.add(which)
                        } else if (selectedAttachments.contains(which)) {
                            selectedAttachments.remove(which)
                        }
                    }
                    setTitle(translations["select_attachments_title"])
                    setNegativeButton(this@MediaDownloader.context.translation["button.cancel"]) { dialog, _ -> dialog.dismiss() }
                    setPositiveButton(this@MediaDownloader.context.translation["button.download"]) { _, _ ->
                        downloadMessageAttachments(friendInfo, message, authorName, selectedAttachments.map { decodedAttachments[it] })
                    }
                }.show()
            }

            return
        }

        runBlocking {
            val firstAttachment = decodedAttachments.first()

            val previewCoroutine = async {
                val downloadedMedia = RemoteMediaResolver.downloadBoltMedia(Base64.decode(firstAttachment.mediaUrlKey!!), decryptionCallback = {
                    firstAttachment.attachmentInfo?.encryption?.decryptInputStream(it) ?: it
                }) ?: return@async null

                val downloadedMediaList = mutableMapOf<SplitMediaAssetType, ByteArray>()

                MediaDownloaderHelper.getSplitElements(ByteArrayInputStream(downloadedMedia)) {
                    type, inputStream ->
                    downloadedMediaList[type] = inputStream.readBytes()
                }

                val originalMedia = downloadedMediaList[SplitMediaAssetType.ORIGINAL] ?: return@async null
                val overlay = downloadedMediaList[SplitMediaAssetType.OVERLAY]

                var bitmap: Bitmap? = PreviewUtils.createPreview(originalMedia, isVideo = FileType.fromByteArray(originalMedia).isVideo)

                if (bitmap == null) {
                    context.shortToast(translations["failed_to_create_preview_toast"])
                    return@async null
                }

                overlay?.also {
                    bitmap = PreviewUtils.mergeBitmapOverlay(bitmap!!, BitmapFactory.decodeByteArray(it, 0, it.size))
                }

                bitmap
            }

            with(ViewAppearanceHelper.newAlertDialogBuilder(context.mainActivity)) {
                val viewGroup = LinearLayout(context).apply {
                    layoutParams = MarginLayoutParams(MarginLayoutParams.MATCH_PARENT, MarginLayoutParams.MATCH_PARENT)
                    gravity = Gravity.CENTER_HORIZONTAL or Gravity.CENTER_VERTICAL
                    addView(ProgressBar(context).apply {
                        isIndeterminate = true
                    })
                }

                setOnDismissListener {
                    previewCoroutine.cancel()
                }

                previewCoroutine.invokeOnCompletion { cause ->
                    runOnUiThread {
                        viewGroup.removeAllViews()
                        if (cause != null) {
                            viewGroup.addView(TextView(context).apply {
                                text = translations["failed_to_create_preview_toast"] + "\n" + cause.message
                                setPadding(30, 30, 30, 30)
                            })
                            return@runOnUiThread
                        }

                        viewGroup.addView(ImageView(context).apply {
                            setImageBitmap(previewCoroutine.getCompleted())
                            layoutParams = LinearLayout.LayoutParams(
                                LinearLayout.LayoutParams.MATCH_PARENT,
                                LinearLayout.LayoutParams.MATCH_PARENT
                            )
                            adjustViewBounds = true
                        })
                    }
                }

                runOnUiThread {
                    show().apply {
                        setContentView(viewGroup)
                        window?.setLayout(
                            context.resources.displayMetrics.widthPixels,
                            context.resources.displayMetrics.heightPixels
                        )
                    }
                    previewCoroutine.start()
                }
            }
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

package me.rhunk.snapenhance.core.features.impl.messaging

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.RemoteInput
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Bundle
import android.os.UserHandle
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.rhunk.snapenhance.common.data.ContentType
import me.rhunk.snapenhance.common.data.MediaReferenceType
import me.rhunk.snapenhance.common.data.MessageUpdate
import me.rhunk.snapenhance.common.data.NotificationType
import me.rhunk.snapenhance.common.data.download.SplitMediaAssetType
import me.rhunk.snapenhance.common.util.protobuf.ProtoReader
import me.rhunk.snapenhance.common.util.snap.MediaDownloaderHelper
import me.rhunk.snapenhance.common.util.snap.RemoteMediaResolver
import me.rhunk.snapenhance.common.util.snap.SnapWidgetBroadcastReceiverHelper
import me.rhunk.snapenhance.core.event.events.impl.SnapWidgetBroadcastReceiveEvent
import me.rhunk.snapenhance.core.features.Feature
import me.rhunk.snapenhance.core.features.FeatureLoadParams
import me.rhunk.snapenhance.core.features.impl.downloader.MediaDownloader
import me.rhunk.snapenhance.core.features.impl.downloader.decoder.MessageDecoder
import me.rhunk.snapenhance.core.features.impl.spying.StealthMode
import me.rhunk.snapenhance.core.util.hook.HookStage
import me.rhunk.snapenhance.core.util.hook.hook
import me.rhunk.snapenhance.core.util.ktx.setObjectField
import me.rhunk.snapenhance.core.util.media.PreviewUtils
import me.rhunk.snapenhance.core.wrapper.impl.Message
import me.rhunk.snapenhance.core.wrapper.impl.SnapUUID
import kotlin.coroutines.suspendCoroutine

class Notifications : Feature("Notifications", loadParams = FeatureLoadParams.INIT_SYNC) {
    inner class NotificationData(
        val tag: String?,
        val id: Int,
        var notification: Notification,
        val userHandle: UserHandle
    ) {
        fun send() {
            XposedBridge.invokeOriginalMethod(notifyAsUserMethod, notificationManager, arrayOf(
                tag, id, notification, userHandle
            ))
        }

        fun copy(tag: String? = this.tag, id: Int = this.id, notification: Notification = this.notification, userHandle: UserHandle = this.userHandle) =
            NotificationData(tag, id, notification, userHandle)
    }

    companion object{
        const val ACTION_REPLY = "me.rhunk.snapenhance.action.notification.REPLY"
        const val ACTION_DOWNLOAD = "me.rhunk.snapenhance.action.notification.DOWNLOAD"
        const val ACTION_MARK_AS_READ = "me.rhunk.snapenhance.action.notification.MARK_AS_READ"
        const val SNAPCHAT_NOTIFICATION_GROUP = "snapchat_notification_group"
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private val coroutineDispatcher = Dispatchers.IO.limitedParallelism(1)
    private val cachedMessages = mutableMapOf<String, MutableMap<Long, String>>() // conversationId => orderKey, message
    private val sentNotifications = mutableMapOf<Int, String>() // notificationId => conversationId

    private val notifyAsUserMethod by lazy {
        XposedHelpers.findMethodExact(
            NotificationManager::class.java, "notifyAsUser",
            String::class.java,
            Int::class.javaPrimitiveType,
            Notification::class.java,
            UserHandle::class.java
        )
    }

    private val notificationManager by lazy {
        context.androidContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    }

    private val translations by lazy { context.translation.getCategory("better_notifications") }

    private val betterNotificationFilter by lazy {
        context.config.messaging.betterNotifications.get()
    }

    private fun newNotificationBuilder(notification: Notification) = XposedHelpers.newInstance(
        Notification.Builder::class.java,
        context.androidContext,
        notification
    ) as Notification.Builder

    private fun computeNotificationMessages(notification: Notification, conversationId: String) {
        val messageText = StringBuilder().apply {
            cachedMessages.computeIfAbsent(conversationId) { sortedMapOf() }.forEach {
                if (isNotEmpty()) append("\n")
                append(it.value)
            }
        }.toString()

        with(notification.extras) {
            putString("android.text", messageText)
            putString("android.bigText", messageText)
            putParcelableArray("android.messages", messageText.split("\n").map {
                Bundle().apply {
                    putBundle("extras", Bundle())
                    putString("text", it)
                    putLong("time", System.currentTimeMillis())
                }
            }.toTypedArray())
        }
    }

    private fun setupNotificationActionButtons(contentType: ContentType, conversationId: String, message: Message, notificationData: NotificationData) {
        val actions = mutableListOf<Notification.Action>()
        actions.addAll(notificationData.notification.actions ?: emptyArray())

        fun newAction(title: String, remoteAction: String, filter: (() -> Boolean), builder: (Notification.Action.Builder) -> Unit) {
            if (!filter()) return

            val intent = SnapWidgetBroadcastReceiverHelper.create(remoteAction) {
                putExtra("conversation_id", conversationId)
                putExtra("notification_id", notificationData.id)
                putExtra("client_message_id", message.messageDescriptor!!.messageId!!)
            }

            val action = Notification.Action.Builder(null, title, PendingIntent.getBroadcast(
                context.androidContext,
                System.nanoTime().toInt(),
                intent,
                PendingIntent.FLAG_MUTABLE
            )).apply(builder).build()
            actions.add(action)
        }

        newAction(translations["button.reply"], ACTION_REPLY, {
            betterNotificationFilter.contains("reply_button") && contentType == ContentType.CHAT
        }) {
            val chatReplyInput = RemoteInput.Builder("chat_reply_input")
                .setLabel(translations["button.reply"])
                .build()
            it.addRemoteInput(chatReplyInput)
        }

        newAction(translations["button.download"], ACTION_DOWNLOAD, {
            betterNotificationFilter.contains("download_button") && betterNotificationFilter.contains("media_preview") && (contentType == ContentType.EXTERNAL_MEDIA || contentType == ContentType.SNAP)
        }) {}

        newAction(translations["button.mark_as_read"], ACTION_MARK_AS_READ, {
            betterNotificationFilter.contains("mark_as_read_button")
        }) {}

        val notificationBuilder = newNotificationBuilder(notificationData.notification).apply {
            setActions(*actions.toTypedArray())
        }
        notificationData.notification = notificationBuilder.build()
    }

    private fun setupBroadcastReceiverHook() {
        context.event.subscribe(SnapWidgetBroadcastReceiveEvent::class) { event ->
            val intent = event.intent ?: return@subscribe
            val conversationId = intent.getStringExtra("conversation_id") ?: return@subscribe
            val clientMessageId = intent.getLongExtra("client_message_id", -1)
            val notificationId = intent.getIntExtra("notification_id", -1)

            val updateNotification: (Int, (Notification) -> Unit) -> Unit = { id, notificationBuilder ->
                notificationManager.activeNotifications.firstOrNull { it.id == id }?.let {
                    notificationBuilder(it.notification)
                    NotificationData(it.tag, it.id, it.notification, it.user).send()
                }
            }

            suspend fun appendNotificationText(input: String) {
                cachedMessages.computeIfAbsent(conversationId) { sortedMapOf() }.let {
                    it[(it.keys.lastOrNull() ?: 0) + 1L] = input
                }

                withContext(Dispatchers.Main) {
                    updateNotification(notificationId) { notification ->
                        notification.flags = notification.flags or Notification.FLAG_ONLY_ALERT_ONCE
                        computeNotificationMessages(notification, conversationId)
                    }
                }
            }

            when (event.action) {
                ACTION_REPLY -> {
                    val input = RemoteInput.getResultsFromIntent(intent).getCharSequence("chat_reply_input")
                        .toString()
                    val myUser = context.database.myUserId.let { context.database.getFriendInfo(it) } ?: return@subscribe

                    context.messageSender.sendChatMessage(listOf(SnapUUID.fromString(conversationId)), input, onError = {
                        context.longToast("Failed to send message: $it")
                        context.coroutineScope.launch(coroutineDispatcher) {
                            appendNotificationText("Failed to send message: $it")
                        }
                    }, onSuccess = {
                        context.coroutineScope.launch(coroutineDispatcher) {
                            appendNotificationText("${myUser.displayName ?: myUser.mutableUsername}: $input")
                        }
                    })
                }
                ACTION_DOWNLOAD -> {
                    runCatching {
                        context.feature(MediaDownloader::class).downloadMessageId(clientMessageId, isPreview = false)
                    }.onFailure {
                        context.longToast(it)
                    }
                }
                ACTION_MARK_AS_READ -> {
                    runCatching {
                        if (context.feature(StealthMode::class).canUseRule(conversationId)) {
                            context.longToast(translations["stealth_mode_notice"])
                            return@subscribe
                        }

                        val conversationManager = context.feature(Messaging::class).conversationManager ?: return@subscribe

                        conversationManager.displayedMessages(
                            conversationId,
                            clientMessageId,
                            onResult = {
                                if (it != null) {
                                    context.log.error("Failed to mark conversation as read: $it")
                                    context.shortToast("Failed to mark conversation as read")
                                }
                            }
                        )

                        if (betterNotificationFilter.contains("mark_as_read_and_save_in_chat")) {
                            val messaging = context.feature(Messaging::class)
                            val autoSave = context.feature(AutoSave::class)

                            if (autoSave.canSaveInConversation(conversationId, headless = true)) {
                                messaging.conversationManager?.fetchConversationWithMessagesPaginated(
                                    conversationId,
                                    Long.MAX_VALUE,
                                    20,
                                    onSuccess = { messages ->
                                        messages.reversed().forEach { message ->
                                            if (!autoSave.canSaveMessage(message, headless = true)) return@forEach
                                            context.coroutineScope.launch(coroutineDispatcher) {
                                                autoSave.saveMessage(conversationId, message)
                                            }
                                        }
                                    },
                                    onError = {
                                        context.log.error("Failed to fetch conversation: $it")
                                        context.shortToast("Failed to fetch conversation")
                                    }
                                )
                            }
                        }

                        val conversationMessage = context.database.getConversationMessageFromId(clientMessageId) ?: return@subscribe

                        if (conversationMessage.contentType == ContentType.SNAP.id) {
                            conversationManager.updateMessage(conversationId, clientMessageId, MessageUpdate.READ) {
                                if (it != null) {
                                    context.log.error("Failed to open snap: $it")
                                    context.shortToast("Failed to open snap")
                                }
                            }
                        }
                    }.onFailure {
                        context.log.error("Failed to mark message as read", it)
                        context.shortToast("Failed to mark message as read. Check logs for more details")
                    }
                    notificationManager.cancel(notificationId)
                }
                else -> return@subscribe
            }

            event.canceled = true
        }
    }

    private fun sendNotification(message: Message, notificationData: NotificationData, forceCreate: Boolean) {
        val conversationId = message.messageDescriptor?.conversationId.toString()
        val notificationId = if (forceCreate) System.nanoTime().toInt() else message.messageDescriptor?.conversationId?.toBytes().contentHashCode()
        sentNotifications.computeIfAbsent(notificationId) { conversationId }

        if (betterNotificationFilter.contains("group")) {
            runCatching {
                if (notificationManager.activeNotifications.firstOrNull {
                    it.notification.flags and Notification.FLAG_GROUP_SUMMARY != 0
                } == null) {
                    notificationManager.notify(
                        notificationData.tag,
                        System.nanoTime().toInt(),
                        Notification.Builder(context.androidContext, notificationData.notification.channelId)
                            .setSmallIcon(notificationData.notification.smallIcon)
                            .setGroup(SNAPCHAT_NOTIFICATION_GROUP)
                            .setGroupSummary(true)
                            .setAutoCancel(true)
                            .setOnlyAlertOnce(true)
                            .build()
                    )
                }
            }.onFailure {
                context.log.warn("Failed to set notification group key: ${it.stackTraceToString()}", featureKey)
            }
        }

        notificationData.copy(id = notificationId).also {
            setupNotificationActionButtons(message.messageContent!!.contentType!!, conversationId, message, it)
        }.send()
    }

    private fun onMessageReceived(data: NotificationData, notificationType: String, message: Message) {
        val conversationId = message.messageDescriptor?.conversationId.toString()
        val orderKey = message.orderKey ?: return
        val senderUsername by lazy {
            context.database.getFriendInfo(message.senderId.toString())?.let {
                it.displayName ?: it.mutableUsername
            } ?: "Unknown"
        }

        val contentType = message.messageContent!!.contentType!!.let { contentType ->
            when {
                notificationType.contains("screenshot") -> ContentType.STATUS_CONVERSATION_CAPTURE_SCREENSHOT
                else -> contentType
            }
        }
        val computeMessages: () -> Unit = { computeNotificationMessages(data.notification, conversationId)}

        fun setNotificationText(text: String) {
            val includeUsername = context.database.getDMOtherParticipant(conversationId) == null
            cachedMessages.computeIfAbsent(conversationId) {
                sortedMapOf()
            }[orderKey] = if (includeUsername) "$senderUsername: $text" else text
        }

        when (
            contentType.takeIf {
                (it != ContentType.SNAP && it != ContentType.EXTERNAL_MEDIA) || betterNotificationFilter.contains("media_preview")
            } ?: ContentType.UNKNOWN
        ) {
            ContentType.CHAT -> {
                ProtoReader(message.messageContent!!.content!!).getString(2, 1)?.trim()?.let {
                    setNotificationText(it)
                }
                computeMessages()
            }
            ContentType.SNAP, ContentType.EXTERNAL_MEDIA -> {
                val mediaReferences = MessageDecoder.getMediaReferences(
                    messageContent = context.gson.toJsonTree(message.messageContent!!.instanceNonNull())
                )

                val mediaReferenceKeys = mediaReferences.map { reference ->
                    reference.asJsonObject.getAsJsonArray("mContentObject").map { it.asByte }.toByteArray()
                }

                MessageDecoder.decode(message.messageContent!!).firstOrNull()?.also { media ->
                    val mediaType = MediaReferenceType.valueOf(mediaReferences.first().asJsonObject["mMediaType"].asString)

                    runCatching {
                        val downloadedMedia = RemoteMediaResolver.downloadBoltMedia(mediaReferenceKeys.first(), decryptionCallback =  {
                            media.attachmentInfo?.encryption?.decryptInputStream(it) ?: it
                        }) ?: throw Throwable("Unable to download media")

                        val downloadedMedias = mutableMapOf<SplitMediaAssetType, ByteArray>()

                        MediaDownloaderHelper.getSplitElements(downloadedMedia.inputStream()) { type, inputStream ->
                            downloadedMedias[type] = inputStream.readBytes()
                        }

                        var bitmapPreview = PreviewUtils.createPreview(downloadedMedias[SplitMediaAssetType.ORIGINAL]!!, mediaType.name.contains("VIDEO"))!!

                        downloadedMedias[SplitMediaAssetType.OVERLAY]?.let {
                            bitmapPreview = PreviewUtils.mergeBitmapOverlay(bitmapPreview, BitmapFactory.decodeByteArray(it, 0, it.size))
                        }

                        val notificationBuilder = newNotificationBuilder(data.notification).apply {
                            setLargeIcon(bitmapPreview)
                            style = Notification.BigPictureStyle().bigPicture(bitmapPreview).bigLargeIcon(null as Bitmap?)
                        }

                        sendNotification(message, data.copy(notification = notificationBuilder.build()), true)
                        return
                    }.onFailure {
                        context.log.error("Failed to send preview notification", it)
                    }
                }
            }
            else -> {
                setNotificationText("[" + context.translation.getCategory("content_type")[contentType.name] + "]")
                computeMessages()
            }
        }
        if (!betterNotificationFilter.contains("chat_preview")) return

        sendNotification(message, data, false)
    }

    override fun init() {
        setupBroadcastReceiverHook()

        notifyAsUserMethod.hook(HookStage.BEFORE) { param ->
            val notificationData = NotificationData(param.argNullable(0), param.arg(1), param.arg(2), param.arg(3))
            val extras = notificationData.notification.extras.getBundle("system_notification_extras")?: return@hook

            if (betterNotificationFilter.contains("group")) {
                notificationData.notification.setObjectField("mGroupKey", SNAPCHAT_NOTIFICATION_GROUP)
            }

            val conversationId = extras.getString("conversation_id").also { id ->
                sentNotifications.computeIfAbsent(notificationData.id) { id ?: "" }
            } ?: return@hook

            val serverMessageId = extras.getString("message_id") ?: return@hook
            val notificationType = extras.getString("notification_type")?.lowercase() ?: return@hook
            if (!betterNotificationFilter.contains("chat_preview") && !betterNotificationFilter.contains("media_preview")) return@hook

            param.setResult(null)
            val conversationManager = context.feature(Messaging::class).conversationManager ?: return@hook

            context.coroutineScope.launch(coroutineDispatcher) {
                suspendCoroutine { continuation ->
                    conversationManager.fetchMessageByServerId(conversationId, serverMessageId.toLong(), onSuccess = {
                        if (it.senderId.toString() == context.database.myUserId) {
                            param.invokeOriginal()
                            continuation.resumeWith(Result.success(Unit))
                            return@fetchMessageByServerId
                        }
                        onMessageReceived(notificationData, notificationType, it)
                        continuation.resumeWith(Result.success(Unit))
                    }, onError = {
                        context.log.error("Failed to fetch message id ${serverMessageId}: $it")
                        param.invokeOriginal()
                        continuation.resumeWith(Result.success(Unit))
                    })
                }
            }
        }

        NotificationManager::class.java.declaredMethods.find {
            it.name == "cancelAsUser"
        }?.hook(HookStage.AFTER) { param ->
            val notificationId = param.arg<Int>(1)

            context.coroutineScope.launch(coroutineDispatcher) {
                sentNotifications[notificationId]?.let {
                    cachedMessages[it]?.clear()
                }
                sentNotifications.remove(notificationId)
            }

            notificationManager.activeNotifications.let { notifications ->
                if (notifications.all { it.notification.flags and Notification.FLAG_GROUP_SUMMARY != 0 }) {
                    notifications.forEach { param.invokeOriginal(arrayOf(it.tag, it.id, it.user)) }
                }
            }
        }

        findClass("com.google.firebase.messaging.FirebaseMessagingService").run {
            val states by context.config.messaging.notificationBlacklist
            methods.first { it.declaringClass == this && it.returnType == Void::class.javaPrimitiveType && it.parameterCount == 1 && it.parameterTypes[0] == Intent::class.java }
                .hook(HookStage.BEFORE) { param ->
                    val intent = param.argNullable<Intent>(0) ?: return@hook
                    val messageType = intent.getStringExtra("type") ?: return@hook

                    context.log.debug("received message type: $messageType")

                    val formattedMessageType = messageType.replaceFirst("mischief_", "")
                        .replaceFirst("group_your_", "group_")
                        .replaceFirst("group_other_", "group_")

                    if (states.mapNotNull { NotificationType.getByKey(it) }.any { it.isMatch(formattedMessageType) }) {
                        param.setResult(null)
                    }
                }
        }
    }
}
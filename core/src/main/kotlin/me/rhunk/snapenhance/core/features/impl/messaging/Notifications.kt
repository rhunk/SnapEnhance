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
import me.rhunk.snapenhance.core.util.CallbackBuilder
import me.rhunk.snapenhance.core.util.hook.HookStage
import me.rhunk.snapenhance.core.util.hook.hook
import me.rhunk.snapenhance.core.util.ktx.setObjectField
import me.rhunk.snapenhance.core.util.media.PreviewUtils
import me.rhunk.snapenhance.core.wrapper.impl.Message
import me.rhunk.snapenhance.core.wrapper.impl.SnapUUID

class Notifications : Feature("Notifications", loadParams = FeatureLoadParams.INIT_SYNC) {
    companion object{
        const val ACTION_REPLY = "me.rhunk.snapenhance.action.notification.REPLY"
        const val ACTION_DOWNLOAD = "me.rhunk.snapenhance.action.notification.DOWNLOAD"
        const val ACTION_MARK_AS_READ = "me.rhunk.snapenhance.action.notification.MARK_AS_READ"
        const val SNAPCHAT_NOTIFICATION_GROUP = "snapchat_notification_group"
    }

    private val notificationDataQueue = mutableMapOf<Long, NotificationData>() // messageId => notification
    private val cachedMessages = mutableMapOf<String, MutableList<String>>() // conversationId => cached messages
    private val notificationIdMap = mutableMapOf<Int, String>() // notificationId => conversationId

    private val notifyAsUserMethod by lazy {
        XposedHelpers.findMethodExact(
            NotificationManager::class.java, "notifyAsUser",
            String::class.java,
            Int::class.javaPrimitiveType,
            Notification::class.java,
            UserHandle::class.java
        )
    }

    private val fetchConversationWithMessagesMethod by lazy {
        context.classCache.conversationManager.methods.first { it.name == "fetchConversationWithMessages"}
    }

    private val notificationManager by lazy {
        context.androidContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    }

    private val translations by lazy { context.translation.getCategory("better_notifications") }

    private val betterNotificationFilter by lazy {
        context.config.messaging.betterNotifications.get()
    }

    private fun setNotificationText(notification: Notification, conversationId: String) {
        val messageText = StringBuilder().apply {
            cachedMessages.computeIfAbsent(conversationId) { mutableListOf() }.forEach {
                if (isNotEmpty()) append("\n")
                append(it)
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

    private fun setupNotificationActionButtons(contentType: ContentType, conversationId: String, messageId: Long, notificationData: NotificationData) {

        val notificationBuilder = XposedHelpers.newInstance(
            Notification.Builder::class.java,
            context.androidContext,
            notificationData.notification
        ) as Notification.Builder

        val actions = mutableListOf<Notification.Action>()
        actions.addAll(notificationData.notification.actions)

        fun newAction(title: String, remoteAction: String, filter: (() -> Boolean), builder: (Notification.Action.Builder) -> Unit) {
            if (!filter()) return

            val intent = SnapWidgetBroadcastReceiverHelper.create(remoteAction) {
                putExtra("conversation_id", conversationId)
                putExtra("notification_id", notificationData.id)
                putExtra("message_id", messageId)
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
            betterNotificationFilter.contains("download_button") && (contentType == ContentType.EXTERNAL_MEDIA || contentType == ContentType.SNAP)
        }) {}

        newAction(translations["button.mark_as_read"], ACTION_MARK_AS_READ, {
            betterNotificationFilter.contains("mark_as_read_button")
        }) {}

        notificationBuilder.setActions(*actions.toTypedArray())
        notificationData.notification = notificationBuilder.build()
    }

    private fun setupBroadcastReceiverHook() {
        context.event.subscribe(SnapWidgetBroadcastReceiveEvent::class) { event ->
            val intent = event.intent ?: return@subscribe
            val conversationId = intent.getStringExtra("conversation_id") ?: return@subscribe
            val messageId = intent.getLongExtra("message_id", -1)
            val notificationId = intent.getIntExtra("notification_id", -1)

            val updateNotification: (Int, (Notification) -> Unit) -> Unit = { id, notificationBuilder ->
                notificationManager.activeNotifications.firstOrNull { it.id == id }?.let {
                    notificationBuilder(it.notification)
                    XposedBridge.invokeOriginalMethod(notifyAsUserMethod, notificationManager, arrayOf(
                        it.tag, it.id, it.notification, it.user
                    ))
                }
            }

            when (event.action) {
                ACTION_REPLY -> {
                    val input = RemoteInput.getResultsFromIntent(intent).getCharSequence("chat_reply_input")
                        .toString()

                    context.database.myUserId.let { context.database.getFriendInfo(it) }?.let { myUser ->
                        cachedMessages.computeIfAbsent(conversationId) { mutableListOf() }.add("${myUser.displayName}: $input")

                        updateNotification(notificationId) { notification ->
                            notification.flags = notification.flags or Notification.FLAG_ONLY_ALERT_ONCE
                            setNotificationText(notification, conversationId)
                        }

                        context.messageSender.sendChatMessage(listOf(SnapUUID.fromString(conversationId)), input, onError = {
                            context.longToast("Failed to send message: $it")
                        })
                    }
                }
                ACTION_DOWNLOAD -> {
                    runCatching {
                        context.feature(MediaDownloader::class).downloadMessageId(messageId, isPreview = false)
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
                            messageId,
                            onResult = {
                                if (it != null) {
                                    context.log.error("Failed to mark conversation as read: $it")
                                    context.shortToast("Failed to mark conversation as read")
                                }
                            }
                        )

                        val conversationMessage = context.database.getConversationMessageFromId(messageId) ?: return@subscribe

                        if (conversationMessage.contentType == ContentType.SNAP.id) {
                            conversationManager.updateMessage(conversationId, messageId, MessageUpdate.READ) {
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

    private fun fetchMessagesResult(conversationId: String, messages: List<Message>) {
        val sendNotificationData = { notificationData: NotificationData, forceCreate: Boolean  ->
            val notificationId = if (forceCreate) System.nanoTime().toInt() else notificationData.id
            notificationIdMap.computeIfAbsent(notificationId) { conversationId }
            if (betterNotificationFilter.contains("group")) {
                runCatching {
                    notificationData.notification.setObjectField("mGroupKey", SNAPCHAT_NOTIFICATION_GROUP)

                    val summaryNotification = Notification.Builder(context.androidContext, notificationData.notification.channelId)
                        .setSmallIcon(notificationData.notification.smallIcon)
                        .setGroup(SNAPCHAT_NOTIFICATION_GROUP)
                        .setGroupSummary(true)
                        .setAutoCancel(true)
                        .setOnlyAlertOnce(true)
                        .build()

                    if (notificationManager.activeNotifications.firstOrNull { it.notification.flags and Notification.FLAG_GROUP_SUMMARY != 0 } == null) {
                        notificationManager.notify(notificationData.tag, notificationData.id, summaryNotification)
                    }
                }.onFailure {
                    context.log.warn("Failed to set notification group key: ${it.stackTraceToString()}", featureKey)
                }
            }

            XposedBridge.invokeOriginalMethod(notifyAsUserMethod, notificationManager, arrayOf(
                notificationData.tag, if (forceCreate) System.nanoTime().toInt() else notificationData.id, notificationData.notification, notificationData.userHandle
            ))
        }

        synchronized(notificationDataQueue) {
            notificationDataQueue.entries.onEach { (messageId, notificationData) ->
                val snapMessage = messages.firstOrNull { message -> message.orderKey == messageId } ?: return
                val senderUsername by lazy {
                    context.database.getFriendInfo(snapMessage.senderId.toString())?.let {
                        it.displayName ?: it.mutableUsername
                    }
                }

                val contentType = snapMessage.messageContent.contentType ?: return@onEach
                val contentData = snapMessage.messageContent.content

                val formatUsername: (String) -> String = { "$senderUsername: $it" }
                val notificationCache = cachedMessages.let { it.computeIfAbsent(conversationId) { mutableListOf() } }
                val appendNotifications: () -> Unit = { setNotificationText(notificationData.notification, conversationId)}

                setupNotificationActionButtons(contentType, conversationId, snapMessage.messageDescriptor.messageId, notificationData)

                when (contentType) {
                    ContentType.NOTE -> {
                        notificationCache.add(formatUsername("sent audio note"))
                        appendNotifications()
                    }
                    ContentType.CHAT -> {
                        ProtoReader(contentData).getString(2, 1)?.trim()?.let {
                            notificationCache.add(formatUsername(it))
                        }
                        appendNotifications()
                    }
                    ContentType.SNAP, ContentType.EXTERNAL_MEDIA -> {
                        val mediaReferences = MessageDecoder.getMediaReferences(
                            messageContent = context.gson.toJsonTree(snapMessage.messageContent.instanceNonNull())
                        )

                        val mediaReferenceKeys = mediaReferences.map { reference ->
                            reference.asJsonObject.getAsJsonArray("mContentObject").map { it.asByte }.toByteArray()
                        }

                        MessageDecoder.decode(snapMessage.messageContent).firstOrNull()?.also { media ->
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

                                val notificationBuilder = XposedHelpers.newInstance(
                                    Notification.Builder::class.java,
                                    context.androidContext,
                                    notificationData.notification
                                ) as Notification.Builder
                                notificationBuilder.setLargeIcon(bitmapPreview)
                                notificationBuilder.style = Notification.BigPictureStyle().bigPicture(bitmapPreview).bigLargeIcon(null as Bitmap?)

                                sendNotificationData(notificationData.copy(notification = notificationBuilder.build()), true)
                                return@onEach
                            }.onFailure {
                                context.log.error("Failed to send preview notification", it)
                            }
                        }
                    }
                    else -> {
                        notificationCache.add(formatUsername("sent ${contentType.name.lowercase()}"))
                        appendNotifications()
                    }
                }

                sendNotificationData(notificationData, false)
            }.clear()
        }
    }

    override fun init() {
        setupBroadcastReceiverHook()

        notifyAsUserMethod.hook(HookStage.BEFORE) { param ->
            val notificationData = NotificationData(param.argNullable(0), param.arg(1), param.arg(2), param.arg(3))

            val extras: Bundle = notificationData.notification.extras.getBundle("system_notification_extras")?: return@hook

            val messageId = extras.getString("message_id") ?: return@hook
            val notificationType = extras.getString("notification_type") ?: return@hook
            val conversationId = extras.getString("conversation_id") ?: return@hook

            if (betterNotificationFilter.map { it.uppercase() }.none {
                    notificationType.contains(it)
                }) return@hook

            synchronized(notificationDataQueue) {
                notificationDataQueue[messageId.toLong()] = notificationData
            }

            context.feature(Messaging::class).conversationManager?.fetchConversationWithMessages(conversationId, onSuccess = { messages ->
                fetchMessagesResult(conversationId, messages)
            }, onError = {
                context.log.error("Failed to fetch conversation with messages: $it")
            })

            param.setResult(null)
        }

        XposedHelpers.findMethodExact(
            NotificationManager::class.java,
            "cancelAsUser", String::class.java,
            Int::class.javaPrimitiveType,
            UserHandle::class.java
        ).hook(HookStage.BEFORE) { param ->
            val notificationId = param.arg<Int>(1)
            notificationIdMap[notificationId]?.let {
                cachedMessages[it]?.clear()
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

    data class NotificationData(
        val tag: String?,
        val id: Int,
        var notification: Notification,
        val userHandle: UserHandle
    )
}
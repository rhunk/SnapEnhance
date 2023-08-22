package me.rhunk.snapenhance.features.impl.tweaks

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
import me.rhunk.snapenhance.Logger
import me.rhunk.snapenhance.core.eventbus.events.impl.SnapWidgetBroadcastReceiveEvent
import me.rhunk.snapenhance.data.ContentType
import me.rhunk.snapenhance.data.MediaReferenceType
import me.rhunk.snapenhance.data.wrapper.impl.Message
import me.rhunk.snapenhance.data.wrapper.impl.SnapUUID
import me.rhunk.snapenhance.download.data.SplitMediaAssetType
import me.rhunk.snapenhance.features.Feature
import me.rhunk.snapenhance.features.FeatureLoadParams
import me.rhunk.snapenhance.features.impl.Messaging
import me.rhunk.snapenhance.features.impl.downloader.MediaDownloader
import me.rhunk.snapenhance.hook.HookStage
import me.rhunk.snapenhance.hook.Hooker
import me.rhunk.snapenhance.hook.hook
import me.rhunk.snapenhance.util.CallbackBuilder
import me.rhunk.snapenhance.util.protobuf.ProtoReader
import me.rhunk.snapenhance.util.snap.EncryptionHelper
import me.rhunk.snapenhance.util.snap.MediaDownloaderHelper
import me.rhunk.snapenhance.util.snap.PreviewUtils
import me.rhunk.snapenhance.util.snap.SnapWidgetBroadcastReceiverHelper

class Notifications : Feature("Notifications", loadParams = FeatureLoadParams.INIT_SYNC) {
    companion object{
        const val ACTION_REPLY = "me.rhunk.snapenhance.action.notification.REPLY"
        const val ACTION_DOWNLOAD = "me.rhunk.snapenhance.action.notification.DOWNLOAD"
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

    private val betterNotificationFilter by lazy {
        context.config.global.betterNotifications.get()
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

        newAction("Reply", ACTION_REPLY, {
            betterNotificationFilter.contains("reply_button") && contentType == ContentType.CHAT
        }) {
            val chatReplyInput = RemoteInput.Builder("chat_reply_input")
                .setLabel("Reply")
                .build()
            it.addRemoteInput(chatReplyInput)
        }

        newAction("Download", ACTION_DOWNLOAD, {
            betterNotificationFilter.contains("download_button") && (contentType == ContentType.EXTERNAL_MEDIA || contentType == ContentType.SNAP)
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
            val notificationManager = event.androidContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

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
                else -> return@subscribe
            }

            event.canceled = true
        }
    }

    private fun fetchMessagesResult(conversationId: String, messages: List<Message>) {
        val sendNotificationData = { notificationData: NotificationData, forceCreate: Boolean  ->
            val notificationId = if (forceCreate) System.nanoTime().toInt() else notificationData.id
            notificationIdMap.computeIfAbsent(notificationId) { conversationId }

            XposedBridge.invokeOriginalMethod(notifyAsUserMethod, notificationManager, arrayOf(
                notificationData.tag, if (forceCreate) System.nanoTime().toInt() else notificationData.id, notificationData.notification, notificationData.userHandle
            ))
        }

        synchronized(notificationDataQueue) {
            notificationDataQueue.entries.onEach { (messageId, notificationData) ->
                val snapMessage = messages.firstOrNull { message -> message.orderKey == messageId } ?: return
                val senderUsername by lazy {
                    context.database.getFriendInfo(snapMessage.senderId.toString())?.let {
                        it.displayName ?: it.username
                    }
                }

                val contentType = snapMessage.messageContent.contentType
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
                    ContentType.SNAP, ContentType.EXTERNAL_MEDIA-> {
                        //serialize the message content into a json object
                        val serializedMessageContent = context.gson.toJsonTree(snapMessage.messageContent.instanceNonNull()).asJsonObject
                        val mediaReferences = serializedMessageContent["mRemoteMediaReferences"]
                            .asJsonArray.map { it.asJsonObject["mMediaReferences"].asJsonArray }
                            .flatten()

                        mediaReferences.forEach { media ->
                            val protoMediaReference = media.asJsonObject["mContentObject"].asJsonArray.map { it.asByte }.toByteArray()
                            val mediaType = MediaReferenceType.valueOf(media.asJsonObject["mMediaType"].asString)
                            runCatching {
                                val messageReader = ProtoReader(contentData)
                                val downloadedMediaList = MediaDownloaderHelper.downloadMediaFromReference(protoMediaReference) {
                                    EncryptionHelper.decryptInputStream(it, contentType, messageReader, isArroyo = false)
                                }

                                var bitmapPreview = PreviewUtils.createPreview(downloadedMediaList[SplitMediaAssetType.ORIGINAL]!!, mediaType.name.contains("VIDEO"))!!

                                downloadedMediaList[SplitMediaAssetType.OVERLAY]?.let {
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
                                Logger.xposedLog("Failed to send preview notification", it)
                            }
                        }
                    }
                    else -> {
                        notificationCache.add(formatUsername("sent $contentType"))
                    }
                }

                sendNotificationData(notificationData, false)
            }.clear()
        }
    }

    override fun init() {
        setupBroadcastReceiverHook()

        val fetchConversationWithMessagesCallback = context.mappings.getMappedClass("callbacks", "FetchConversationWithMessagesCallback")

        Hooker.hook(notifyAsUserMethod, HookStage.BEFORE) { param ->
            val notificationData = NotificationData(param.argNullable(0), param.arg(1), param.arg(2), param.arg(3))

            val extras: Bundle = notificationData.notification.extras.getBundle("system_notification_extras")?: return@hook

            val messageId = extras.getString("message_id") ?: return@hook
            val notificationType = extras.getString("notification_type") ?: return@hook
            val conversationId = extras.getString("conversation_id") ?: return@hook

            if (betterNotificationFilter.map { it.uppercase() }.none {
                    notificationType.contains(it)
                }) return@hook

            val conversationManager: Any = context.feature(Messaging::class).conversationManager

            synchronized(notificationDataQueue) {
                notificationDataQueue[messageId.toLong()] = notificationData
            }

            val callback = CallbackBuilder(fetchConversationWithMessagesCallback)
                .override("onFetchConversationWithMessagesComplete") { callbackParam ->
                    val messageList = (callbackParam.arg(1) as List<Any>).map { msg -> Message(msg) }
                    fetchMessagesResult(conversationId, messageList)
                }
                .override("onError") {
                    Logger.xposedLog("Failed to fetch message ${it.arg(0) as Any}")
                }.build()

            fetchConversationWithMessagesMethod.invoke(conversationManager, SnapUUID.fromString(conversationId).instanceNonNull(), callback)
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
            val states by context.config.global.notificationBlacklist
            methods.first { it.declaringClass == this && it.returnType == Void::class.javaPrimitiveType && it.parameterCount == 1 && it.parameterTypes[0] == Intent::class.java }
                .hook(HookStage.BEFORE) { param ->
                    val intent = param.argNullable<Intent>(0) ?: return@hook
                    val messageType = intent.getStringExtra("type") ?: return@hook

                    Logger.xposedLog("received message type: $messageType")

                    if (states.contains(messageType.replaceFirst("mischief_", ""))) {
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
package me.rhunk.snapenhance.features.impl.extras

import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import android.graphics.Bitmap
import android.os.Bundle
import android.os.UserHandle
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import me.rhunk.snapenhance.Constants
import me.rhunk.snapenhance.Logger
import me.rhunk.snapenhance.config.ConfigProperty
import me.rhunk.snapenhance.data.ContentType
import me.rhunk.snapenhance.data.MediaReferenceType
import me.rhunk.snapenhance.data.wrapper.impl.Message
import me.rhunk.snapenhance.data.wrapper.impl.SnapUUID
import me.rhunk.snapenhance.features.Feature
import me.rhunk.snapenhance.features.FeatureLoadParams
import me.rhunk.snapenhance.features.impl.Messaging
import me.rhunk.snapenhance.hook.HookStage
import me.rhunk.snapenhance.hook.Hooker
import me.rhunk.snapenhance.util.CallbackBuilder
import me.rhunk.snapenhance.util.EncryptionUtils
import me.rhunk.snapenhance.util.MediaDownloaderHelper
import me.rhunk.snapenhance.util.MediaType
import me.rhunk.snapenhance.util.PreviewUtils
import me.rhunk.snapenhance.util.protobuf.ProtoReader

class Notifications : Feature("Notifications", loadParams = FeatureLoadParams.INIT_SYNC) {
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

    private val cancelAsUserMethod by lazy {
        XposedHelpers.findMethodExact(NotificationManager::class.java, "cancelAsUser", String::class.java, Int::class.javaPrimitiveType, UserHandle::class.java)
    }

    private val fetchConversationWithMessagesMethod by lazy {
        context.classCache.conversationManager.methods.first { it.name == "fetchConversationWithMessages"}
    }

    private val notificationManager by lazy {
        context.androidContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    }

    private fun setNotificationText(notification: NotificationData, text: String) {
        with(notification.notification.extras) {
            putString("android.text", text)
            putString("android.bigText", text)
        }
    }

    private fun computeNotificationText(conversationId: String): String {
        val messageBuilder = StringBuilder()
        cachedMessages.computeIfAbsent(conversationId) { mutableListOf() }.forEach {
            if (messageBuilder.isNotEmpty()) messageBuilder.append("\n")
            messageBuilder.append(it)
        }
        return messageBuilder.toString()
    }

    private fun fetchMessagesResult(conversationId: String, messages: List<Message>) {
        val sendNotificationData = { notificationData: NotificationData, forceCreate: Boolean  ->
            val notificationId = if (forceCreate) System.nanoTime().toInt() else notificationData.id
            notificationIdMap.computeIfAbsent(notificationId) { conversationId }

            XposedBridge.invokeOriginalMethod(notifyAsUserMethod, notificationManager, arrayOf(
                notificationData.tag, if (forceCreate) System.nanoTime().toInt() else notificationData.id, notificationData.notification, notificationData.userHandle
            ))
        }

        notificationDataQueue.entries.onEach { (messageId, notificationData) ->
            val snapMessage = messages.firstOrNull { message -> message.orderKey == messageId } ?: return
            val senderUsername = context.database.getFriendInfo(snapMessage.senderId.toString())?.displayName ?: throw Throwable("Cant find senderId of message $snapMessage")

            val contentType = snapMessage.messageContent.contentType
            val contentData = snapMessage.messageContent.content

            val formatUsername: (String) -> String = { "$senderUsername: $it" }
            val notificationCache = cachedMessages.let { it.computeIfAbsent(conversationId) { mutableListOf() } }
            val appendNotifications: () -> Unit = { setNotificationText(notificationData, computeNotificationText(conversationId))}

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
                        val mediaContent = media.asJsonObject["mContentObject"].asJsonArray.map { it.asByte }.toByteArray()
                        val mediaType = MediaReferenceType.valueOf(media.asJsonObject["mMediaType"].asString)
                        val urlKey = ProtoReader(mediaContent).getString(2, 2) ?: return@forEach
                        runCatching {
                            //download the media
                            val mediaInfo = ProtoReader(contentData).let {
                                if (contentType == ContentType.EXTERNAL_MEDIA)
                                    return@let it.readPath(*Constants.MESSAGE_EXTERNAL_MEDIA_ENCRYPTION_PROTO_PATH)
                                else
                                    return@let it.readPath(*Constants.MESSAGE_SNAP_ENCRYPTION_PROTO_PATH)
                            }?: return@runCatching

                            val downloadedMedia = MediaDownloaderHelper.downloadMediaFromKey(urlKey, mergeOverlay = false, isPreviewMode = false) {
                                if (mediaInfo.exists(Constants.ARROYO_ENCRYPTION_PROTO_INDEX))
                                    EncryptionUtils.decryptInputStream(it, false, mediaInfo, Constants.ARROYO_ENCRYPTION_PROTO_INDEX)
                                else it
                            }[MediaType.ORIGINAL] ?: throw Throwable("Failed to download media from key $urlKey")

                            val bitmapPreview = PreviewUtils.createPreview(downloadedMedia, mediaType == MediaReferenceType.VIDEO)!!
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
                            Logger.xposedLog("urlKey: $urlKey")
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

    private fun shouldIgnoreNotification(type: String): Boolean {
        val states = context.config.options(ConfigProperty.NOTIFICATION_BLACKLIST)

        states["snap"]?.let { if (type.endsWith("SNAP") && it) return true }
        states["chat"]?.let { if (type.endsWith("CHAT") && it) return true }
        states["typing"]?.let { if (type.endsWith("TYPING") && it) return true }

        return false
    }

    override fun init() {
        val fetchConversationWithMessagesCallback = context.mappings.getMappedClass("callbacks", "FetchConversationWithMessagesCallback")

        Hooker.hook(notifyAsUserMethod, HookStage.BEFORE) { param ->
            val notificationData = NotificationData(param.argNullable(0), param.arg(1), param.arg(2), param.arg(3))

            val extras: Bundle = notificationData.notification.extras.getBundle("system_notification_extras")?: return@hook

            val messageId = extras.getString("message_id") ?: return@hook
            val notificationType = extras.getString("notification_type") ?: return@hook
            val conversationId = extras.getString("conversation_id") ?: return@hook

            if (shouldIgnoreNotification(notificationType)) {
                param.setResult(null)
                return@hook
            }

            if (!context.config.bool(ConfigProperty.SHOW_MESSAGE_CONTENT_IN_NOTIFICATIONS)) return@hook

            if (!notificationType.endsWith("CHAT") && !notificationType.endsWith("SNAP")) return@hook

            val conversationManager: Any = context.feature(Messaging::class).conversationManager
            notificationDataQueue[messageId.toLong()] = notificationData

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

        Hooker.hook(cancelAsUserMethod, HookStage.BEFORE) { param ->
            val notificationId = param.arg<Int>(1)
            notificationIdMap[notificationId]?.let {
                cachedMessages[it]?.clear()
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
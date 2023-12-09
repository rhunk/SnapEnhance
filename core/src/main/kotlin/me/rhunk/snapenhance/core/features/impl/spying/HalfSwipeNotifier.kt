package me.rhunk.snapenhance.core.features.impl.spying

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import me.rhunk.snapenhance.common.Constants
import me.rhunk.snapenhance.core.features.Feature
import me.rhunk.snapenhance.core.features.FeatureLoadParams
import me.rhunk.snapenhance.core.util.hook.HookStage
import me.rhunk.snapenhance.core.util.hook.hook
import me.rhunk.snapenhance.core.util.hook.hookConstructor
import me.rhunk.snapenhance.core.util.ktx.getIdentifier
import me.rhunk.snapenhance.core.util.ktx.getObjectField
import java.util.concurrent.ConcurrentHashMap
import kotlin.time.Duration.Companion.milliseconds

class HalfSwipeNotifier : Feature("Half Swipe Notifier", loadParams = FeatureLoadParams.INIT_SYNC) {
    private val peekingConversations = ConcurrentHashMap<String, List<String>>()
    private val startPeekingTimestamps = ConcurrentHashMap<String, Long>()

    private val svgEyeDrawable by lazy { context.resources.getIdentifier("svg_eye_24x24", "drawable") }
    private val notificationManager get() = context.androidContext.getSystemService(NotificationManager::class.java)
    private val translation by lazy { context.translation.getCategory("half_swipe_notifier")}
    private val channelId by lazy {
        "peeking".also {
            notificationManager.createNotificationChannel(
                NotificationChannel(
                    it,
                    translation["notification_channel_name"],
                    NotificationManager.IMPORTANCE_HIGH
                )
            )
        }
    }


    override fun init() {
        if (context.config.messaging.halfSwipeNotifier.globalState != true) return
        lateinit var presenceService: Any

        findClass("com.snapchat.talkcorev3.PresenceService\$CppProxy").hookConstructor(HookStage.AFTER) {
            presenceService = it.thisObject()
        }

        context.mappings.getMappedClass("callbacks", "PresenceServiceDelegate")
            .hook("notifyActiveConversationsChanged", HookStage.BEFORE) {
                val activeConversations = presenceService::class.java.methods.find { it.name == "getActiveConversations" }?.invoke(presenceService) as? Map<*, *> ?: return@hook // conversationId, conversationInfo (this.mPeekingParticipants)

                if (activeConversations.isEmpty()) {
                    peekingConversations.forEach {
                        val conversationId = it.key
                        val peekingParticipantsIds = it.value
                        peekingParticipantsIds.forEach { userId ->
                            endPeeking(conversationId, userId)
                        }
                    }
                    peekingConversations.clear()
                    return@hook
                }

                activeConversations.forEach { (conversationId, conversationInfo) ->
                    val peekingParticipantsIds = (conversationInfo?.getObjectField("mPeekingParticipants") as? List<*>)?.map { it.toString() } ?: return@forEach
                    val cachedPeekingParticipantsIds = peekingConversations[conversationId] ?: emptyList()

                    val newPeekingParticipantsIds = peekingParticipantsIds - cachedPeekingParticipantsIds.toSet()
                    val exitedPeekingParticipantsIds = cachedPeekingParticipantsIds - peekingParticipantsIds.toSet()

                    newPeekingParticipantsIds.forEach { userId ->
                        startPeeking(conversationId.toString(), userId)
                    }

                    exitedPeekingParticipantsIds.forEach { userId ->
                        endPeeking(conversationId.toString(), userId)
                    }
                    peekingConversations[conversationId.toString()] = peekingParticipantsIds
                }
        }
    }

    private fun startPeeking(conversationId: String, userId: String) {
        startPeekingTimestamps[conversationId + userId] = System.currentTimeMillis()
    }

    private fun endPeeking(conversationId: String, userId: String) {
        startPeekingTimestamps[conversationId + userId]?.let { startPeekingTimestamp ->
            val peekingDuration = (System.currentTimeMillis() - startPeekingTimestamp).milliseconds.inWholeSeconds
            val minDuration = context.config.messaging.halfSwipeNotifier.minDuration.get().toLong()
            val maxDuration = context.config.messaging.halfSwipeNotifier.maxDuration.get().toLong()

            if (minDuration > peekingDuration || maxDuration < peekingDuration) return

            val groupName = context.database.getFeedEntryByConversationId(conversationId)?.feedDisplayName
            val friendInfo = context.database.getFriendInfo(userId) ?: return

            Notification.Builder(context.androidContext, channelId)
                .setContentTitle(groupName ?: friendInfo.displayName ?: friendInfo.mutableUsername)
                .setContentText(if (groupName != null) {
                    translation.format("notification_content_group",
                        "friend" to (friendInfo.displayName ?: friendInfo.mutableUsername).toString(),
                        "group" to groupName,
                        "duration" to peekingDuration.toString()
                    )
                } else {
                    translation.format("notification_content_dm",
                        "friend" to (friendInfo.displayName ?: friendInfo.mutableUsername).toString(),
                        "duration" to peekingDuration.toString()
                    )
                })
                .setContentIntent(
                    context.androidContext.packageManager.getLaunchIntentForPackage(
                        Constants.SNAPCHAT_PACKAGE_NAME
                    )?.let {
                        PendingIntent.getActivity(
                            context.androidContext,
                            0, it, PendingIntent.FLAG_IMMUTABLE
                        )
                    }
                )
                .setWhen(System.currentTimeMillis())
                .setShowWhen(true)
                .setAutoCancel(true)
                .setSmallIcon(svgEyeDrawable)
                .build()
                .let { notification ->
                    notificationManager.notify(System.nanoTime().toInt(), notification)
                }
        }
    }
}
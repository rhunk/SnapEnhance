package me.rhunk.snapenhance.core.features.impl.experiments

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import me.rhunk.snapenhance.common.Constants
import me.rhunk.snapenhance.common.data.*
import me.rhunk.snapenhance.common.util.protobuf.ProtoReader
import me.rhunk.snapenhance.common.util.toParcelable
import me.rhunk.snapenhance.core.features.Feature
import me.rhunk.snapenhance.core.features.FeatureLoadParams
import me.rhunk.snapenhance.core.features.impl.messaging.Messaging
import me.rhunk.snapenhance.core.util.hook.HookStage
import me.rhunk.snapenhance.core.util.hook.hook
import me.rhunk.snapenhance.core.util.hook.hookConstructor
import me.rhunk.snapenhance.core.wrapper.impl.toSnapUUID
import me.rhunk.snapenhance.nativelib.NativeLib
import java.lang.reflect.Method
import java.nio.ByteBuffer

class SessionEvents : Feature("Session Events", loadParams = FeatureLoadParams.INIT_SYNC) {
    private val conversationPresenceState = mutableMapOf<String, MutableMap<String, FriendPresenceState?>>() // conversationId -> (userId -> state)
    private val tracker by lazy { context.bridgeClient.getTracker() }

    private fun getTrackedEvents(eventType: TrackerEventType): TrackerEventsResult? {
        return runCatching {
            tracker.getTrackedEvents(eventType.key)?.let {
                toParcelable<TrackerEventsResult>(it)
            }
        }.onFailure {
            context.log.error("Failed to get tracked events for $eventType", it)
        }.getOrNull()
    }

    private fun isInConversation(conversationId: String?) = context.feature(Messaging::class).openedConversationUUID?.toString() == conversationId

    private fun sendInfoNotification(id: Int = System.nanoTime().toInt(), text: String) {
        context.androidContext.getSystemService(NotificationManager::class.java).notify(
            id,
                Notification.Builder(
                    context.androidContext,
                    "general_group_generic_push_noisy_generic_push_B~LVSD2"
                )
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setAutoCancel(true)
                .setShowWhen(true)
                .setWhen(System.currentTimeMillis())
                .setContentIntent(context.androidContext.packageManager.getLaunchIntentForPackage(
                    Constants.SNAPCHAT_PACKAGE_NAME
                )?.let {
                    PendingIntent.getActivity(
                        context.androidContext,
                        0, it, PendingIntent.FLAG_IMMUTABLE
                    )
                })
                .setContentText(text)
                .build()
        )
    }

    private fun handleVolatileEvent(protoReader: ProtoReader) {
        context.log.verbose("volatile event\n$protoReader")
    }

    private fun dispatchEvents(
        eventType: TrackerEventType,
        conversationId: String,
        userId: String,
        extras: String = ""
    ) {
        val feedEntry = context.database.getFeedEntryByConversationId(conversationId)
        val conversationName = feedEntry?.feedDisplayName ?: "DMs"
        val authorName = context.database.getFriendInfo(userId)?.mutableUsername ?: "Unknown"

        context.log.verbose("$authorName $eventType in $conversationName")

        getTrackedEvents(eventType)?.takeIf { it.canTrackOn(conversationId, userId) }?.getActions()?.forEach { (action, params) ->
            if ((params.onlyWhenAppActive || action == TrackerRuleAction.IN_APP_NOTIFICATION) && context.isMainActivityPaused) return@forEach
            if (params.onlyWhenAppInactive && !context.isMainActivityPaused) return@forEach
            if (params.onlyInsideConversation && !isInConversation(conversationId)) return@forEach
            if (params.onlyOutsideConversation && isInConversation(conversationId)) return@forEach

            context.log.verbose("dispatching $action for $eventType in $conversationName")

            when (action) {
                TrackerRuleAction.PUSH_NOTIFICATION -> {
                    if (params.noPushNotificationWhenAppActive && !context.isMainActivityPaused) return@forEach
                    sendInfoNotification(text = "$authorName $eventType in $conversationName")
                }
                TrackerRuleAction.IN_APP_NOTIFICATION -> context.inAppOverlay.showStatusToast(
                    icon = Icons.Default.Info,
                    text = "$authorName $eventType in $conversationName"
                )
                TrackerRuleAction.LOG -> context.bridgeClient.getMessageLogger().logTrackerEvent(
                    conversationId,
                    conversationName,
                    context.database.getConversationType(conversationId) == 1,
                    authorName,
                    userId,
                    eventType.key,
                    extras
                )
                else -> {}
            }
        }
    }

    private fun onConversationPresenceUpdate(conversationId: String, userId: String, oldState: FriendPresenceState?, currentState: FriendPresenceState?) {
        context.log.verbose("presence state for $userId in conversation $conversationId\n$currentState")

        val eventType = when {
            (oldState == null || currentState?.bitmojiPresent == false) && currentState?.bitmojiPresent == true -> TrackerEventType.CONVERSATION_ENTER
            (currentState == null || oldState?.bitmojiPresent == false) && oldState?.bitmojiPresent == true -> TrackerEventType.CONVERSATION_EXIT
            oldState?.typing == false && currentState?.typing == true -> if (currentState.speaking) TrackerEventType.STARTED_SPEAKING else TrackerEventType.STARTED_TYPING
            oldState?.typing == true && (currentState == null || !currentState.typing) -> if (oldState.speaking) TrackerEventType.STOPPED_SPEAKING else TrackerEventType.STOPPED_TYPING
            (oldState == null || !oldState.peeking) && currentState?.peeking == true -> TrackerEventType.STARTED_PEEKING
            oldState?.peeking == true && (currentState == null || !currentState.peeking) -> TrackerEventType.STOPPED_PEEKING
            else -> null
        } ?: return

        dispatchEvents(eventType, conversationId, userId)
    }

    private fun onConversationMessagingEvent(event: SessionEvent) {
        context.log.verbose("conversation messaging event\n${event.type} in ${event.conversationId} from ${event.authorUserId}")

        val eventType = when(event.type) {
            SessionEventType.MESSAGE_READ_RECEIPTS -> TrackerEventType.MESSAGE_READ
            SessionEventType.MESSAGE_DELETED -> TrackerEventType.MESSAGE_DELETED
            SessionEventType.MESSAGE_REACTION_ADD -> TrackerEventType.MESSAGE_REACTION_ADD
            SessionEventType.MESSAGE_REACTION_REMOVE -> TrackerEventType.MESSAGE_REACTION_REMOVE
            SessionEventType.MESSAGE_SAVED -> TrackerEventType.MESSAGE_SAVED
            SessionEventType.MESSAGE_UNSAVED -> TrackerEventType.MESSAGE_UNSAVED
            SessionEventType.SNAP_OPENED -> TrackerEventType.SNAP_OPENED
            SessionEventType.SNAP_REPLAYED -> TrackerEventType.SNAP_REPLAYED
            SessionEventType.SNAP_REPLAYED_TWICE -> TrackerEventType.SNAP_REPLAYED_TWICE
            SessionEventType.SNAP_SCREENSHOT -> TrackerEventType.SNAP_SCREENSHOT
            SessionEventType.SNAP_SCREEN_RECORD -> TrackerEventType.SNAP_SCREEN_RECORD
            else -> return
        }

        val conversationMessage by lazy {
            (event as? SessionMessageEvent)?.serverMessageId?.let { context.database.getConversationServerMessage(event.conversationId, it) }
        }

        dispatchEvents(eventType, event.conversationId, event.authorUserId, extras = conversationMessage?.takeIf {
            eventType == TrackerEventType.MESSAGE_READ ||
            eventType == TrackerEventType.MESSAGE_REACTION_ADD ||
            eventType == TrackerEventType.MESSAGE_REACTION_REMOVE ||
            eventType == TrackerEventType.MESSAGE_DELETED ||
            eventType == TrackerEventType.MESSAGE_SAVED ||
            eventType == TrackerEventType.MESSAGE_UNSAVED
        }?.contentType?.let { ContentType.fromId(it).name } ?: "")
    }

    private fun handlePresenceEvent(protoReader: ProtoReader) {
        val conversationId = protoReader.getString(6) ?: return

        val presenceMap = conversationPresenceState.getOrPut(conversationId) { mutableMapOf() }.toMutableMap()
        val userIds = mutableSetOf<String>()

        protoReader.eachBuffer(4) {
            val participantUserId = getString(1)?.takeIf { it.contains(":") }?.substringBefore(":") ?: return@eachBuffer
            userIds.add(participantUserId)
            if (participantUserId == context.database.myUserId) return@eachBuffer
            val stateMap = getVarInt(2, 1)?.toString(2)?.padStart(16, '0')?.reversed()?.map { it == '1' } ?: return@eachBuffer

            presenceMap[participantUserId] = FriendPresenceState(
                bitmojiPresent = stateMap[0],
                typing = stateMap[4],
                wasTyping = stateMap[5],
                speaking = stateMap[6] && stateMap[4],
                peeking = stateMap[8]
            )
        }

        presenceMap.keys.filterNot { it in userIds }.forEach { presenceMap[it] = null }

        presenceMap.forEach { (userId, state) ->
            val oldState = conversationPresenceState[conversationId]?.get(userId)
            if (oldState != state) {
                onConversationPresenceUpdate(conversationId, userId, oldState, state)
            }
        }

        conversationPresenceState[conversationId] = presenceMap
    }

    private fun handleMessagingEvent(protoReader: ProtoReader) {
        // read receipts
        protoReader.followPath(12) {
            val conversationId = getByteArray(1, 1)?.toSnapUUID()?.toString() ?: return@followPath

            followPath(7) readReceipts@{
                val senderId = getByteArray(1, 1)?.toSnapUUID()?.toString() ?: return@readReceipts
                val serverMessageId = getVarInt(2, 2) ?: return@readReceipts

                onConversationMessagingEvent(
                    SessionMessageEvent(
                        SessionEventType.MESSAGE_READ_RECEIPTS,
                        conversationId,
                        senderId,
                        serverMessageId,
                    )
                )
            }
        }

        protoReader.followPath(6, 2) {
            val conversationId = getByteArray(3, 1)?.toSnapUUID()?.toString() ?: return@followPath
            val senderId = getByteArray(1, 1)?.toSnapUUID()?.toString() ?: return@followPath
            val serverMessageId = getVarInt(2) ?: return@followPath

            if (contains(4)) {
                onConversationMessagingEvent(
                    SessionMessageEvent(
                        SessionEventType.SNAP_OPENED,
                        conversationId,
                        senderId,
                        serverMessageId
                    )
                )
            }

            if (contains(13)) {
                onConversationMessagingEvent(
                    SessionMessageEvent(
                        if (getVarInt(13, 1) == 2L) SessionEventType.SNAP_REPLAYED_TWICE else SessionEventType.SNAP_REPLAYED,
                        conversationId,
                        senderId,
                        serverMessageId
                    )
                )
            }

            if (contains(6) || contains(7)) {
                onConversationMessagingEvent(
                    SessionMessageEvent(
                        if (contains(6)) SessionEventType.MESSAGE_SAVED else SessionEventType.MESSAGE_UNSAVED,
                        conversationId,
                        senderId,
                        serverMessageId
                    )
                )
            }

            if (contains(11) || contains(12)) {
                onConversationMessagingEvent(
                    SessionMessageEvent(
                        if (contains(11)) SessionEventType.SNAP_SCREENSHOT else SessionEventType.SNAP_SCREEN_RECORD,
                        conversationId,
                        senderId,
                        serverMessageId,
                    )
                )
            }

            followPath(16) {
                onConversationMessagingEvent(
                    SessionMessageEvent(
                        SessionEventType.MESSAGE_REACTION_ADD, conversationId, senderId, serverMessageId, reactionId = getVarInt(1, 1, 1)?.toInt() ?: -1
                    )
                )
            }

            if (contains(17)) {
                onConversationMessagingEvent(
                    SessionMessageEvent(SessionEventType.MESSAGE_REACTION_REMOVE, conversationId, senderId, serverMessageId)
                )
            }

            followPath(8) {
                onConversationMessagingEvent(
                    SessionMessageEvent(SessionEventType.MESSAGE_DELETED, conversationId, senderId, serverMessageId, messageData = getByteArray(1))
                )
            }
        }
    }

    override fun init() {
        val sessionEventsConfig = context.config.experimental.sessionEvents
        if (sessionEventsConfig.globalState != true) return

        if (sessionEventsConfig.allowRunningInBackground.get()) {
            findClass("com.snapchat.client.duplex.DuplexClient\$CppProxy").apply {
                // prevent disabling events when the app is inactive
                hook("appStateChanged", HookStage.BEFORE) { param ->
                    if (param.arg<Any>(0).toString() == "INACTIVE") param.setResult(null)
                }
                // allow events when a notification is received
                hookConstructor(HookStage.AFTER) { param ->
                    methods.first { it.name == "appStateChanged" }.let { method ->
                        method.invoke(param.thisObject(), method.parameterTypes[0].enumConstants.first { it.toString() == "ACTIVE" })
                    }
                }
            }
        }

        if (sessionEventsConfig.captureDuplexEvents.get()) {
            val messageHandlerClass = findClass("com.snapchat.client.duplex.MessageHandler\$CppProxy").apply {
                hook("onReceive", HookStage.BEFORE) { param ->
                    param.setResult(null)

                    val byteBuffer = param.arg<ByteBuffer>(0)
                    val content = byteBuffer.let {
                        val bytes = ByteArray(it.limit())
                        it.get(bytes)
                        bytes
                    }
                    val reader = ProtoReader(content)
                    reader.getString(1, 1)?.let {
                        val eventData = reader.followPath(1, 2) ?: return@let
                        if (it == "volatile") {
                            handleVolatileEvent(eventData)
                            return@hook
                        }

                        if (it == "presence") {
                            handlePresenceEvent(eventData)
                            return@hook
                        }
                    }
                    handleMessagingEvent(reader)
                }
                hook("nativeDestroy", HookStage.BEFORE) { it.setResult(null) }
            }


            findClass("com.snapchat.client.messaging.Session").hook("create", HookStage.BEFORE) { param ->
                if (!NativeLib.initialized) {
                    context.log.warn("Can't register duplex message handler, native lib not initialized")
                    return@hook
                }

                val method = param.method() as Method
                val duplexClient = method.parameterTypes.indexOfFirst { it.name.endsWith("DuplexClient") }.let {
                    param.arg<Any>(it)
                }
                val dispatchQueue = method.parameterTypes.indexOfFirst { it.name.endsWith("DispatchQueue") }.let {
                    param.arg<Any>(it)
                }
                for (channel in arrayOf("pcs", "mcs")) {
                    duplexClient::class.java.methods.first {
                        it.name == "registerHandler"
                    }.invoke(
                        duplexClient,
                        channel,
                        messageHandlerClass.declaredConstructors.first().also { it.isAccessible = true }.newInstance(-1),
                        dispatchQueue
                    )
                }
            }
        }
    }
}
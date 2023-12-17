package me.rhunk.snapenhance.core.features.impl.spying

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.drawable.ShapeDrawable
import android.graphics.drawable.shapes.Shape
import android.os.DeadObjectException
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import me.rhunk.snapenhance.common.data.ContentType
import me.rhunk.snapenhance.common.data.MessageState
import me.rhunk.snapenhance.common.util.ktx.longHashCode
import me.rhunk.snapenhance.common.util.protobuf.ProtoReader
import me.rhunk.snapenhance.core.event.events.impl.BindViewEvent
import me.rhunk.snapenhance.core.event.events.impl.BuildMessageEvent
import me.rhunk.snapenhance.core.features.Feature
import me.rhunk.snapenhance.core.features.FeatureLoadParams
import me.rhunk.snapenhance.core.ui.addForegroundDrawable
import me.rhunk.snapenhance.core.ui.removeForegroundDrawable
import me.rhunk.snapenhance.core.util.EvictingMap
import java.util.concurrent.Executors
import kotlin.system.measureTimeMillis

class MessageLogger : Feature("MessageLogger",
    loadParams = FeatureLoadParams.INIT_SYNC or
        FeatureLoadParams.ACTIVITY_CREATE_SYNC or
        FeatureLoadParams.ACTIVITY_CREATE_ASYNC
) {
    companion object {
        const val PREFETCH_MESSAGE_COUNT = 20
        const val PREFETCH_FEED_COUNT = 20
        const val DELETED_MESSAGE_COLOR = 0x2Eb71c1c
    }

    private val messageLoggerInterface by lazy { context.bridgeClient.getMessageLogger() }

    val isEnabled get() = context.config.messaging.messageLogger.globalState == true

    private val threadPool = Executors.newFixedThreadPool(10)

    private val cachedIdLinks = mutableMapOf<Long, Long>() // client id -> server id
    private val fetchedMessages = mutableListOf<Long>() // list of unique message ids
    private val deletedMessageCache = EvictingMap<Long, JsonObject>(200) // unique message id -> message json object

    fun isMessageDeleted(conversationId: String, clientMessageId: Long)
        = makeUniqueIdentifier(conversationId, clientMessageId)?.let { deletedMessageCache.containsKey(it) } ?: false

    fun deleteMessage(conversationId: String, clientMessageId: Long) {
        val uniqueMessageId = makeUniqueIdentifier(conversationId, clientMessageId) ?: return
        fetchedMessages.remove(uniqueMessageId)
        deletedMessageCache.remove(uniqueMessageId)
        messageLoggerInterface.deleteMessage(conversationId, uniqueMessageId)
    }

    fun getMessageObject(conversationId: String, clientMessageId: Long): JsonObject? {
        val uniqueMessageId = makeUniqueIdentifier(conversationId, clientMessageId) ?: return null
        if (deletedMessageCache.containsKey(uniqueMessageId)) {
            return deletedMessageCache[uniqueMessageId]
        }
        return messageLoggerInterface.getMessage(conversationId, uniqueMessageId)?.let {
            JsonParser.parseString(it.toString(Charsets.UTF_8)).asJsonObject
        }
    }

    fun getMessageProto(conversationId: String, clientMessageId: Long): ProtoReader? {
        return getMessageObject(conversationId, clientMessageId)?.let { message ->
            ProtoReader(message.getAsJsonObject("mMessageContent").getAsJsonArray("mContent")
                .map { it.asByte }
                .toByteArray())
        }
    }

    private fun computeMessageIdentifier(conversationId: String, orderKey: Long) = (orderKey.toString() + conversationId).longHashCode()

    private fun makeUniqueIdentifier(conversationId: String, clientMessageId: Long): Long? {
        val serverMessageId = cachedIdLinks[clientMessageId] ?:
            context.database.getConversationMessageFromId(clientMessageId)?.serverMessageId?.toLong()?.also {
                cachedIdLinks[clientMessageId] = it
            }
            ?: return run {
                context.log.error("Failed to get server message id for $conversationId $clientMessageId")
                null
            }
        return computeMessageIdentifier(conversationId, serverMessageId)
    }

    override fun asyncOnActivityCreate() {
        if (!isEnabled || !context.database.hasArroyo()) {
            return
        }

        measureTimeMillis {
            val conversationIds = context.database.getFeedEntries(PREFETCH_FEED_COUNT).map { it.key!! }
            if (conversationIds.isEmpty()) return@measureTimeMillis
            fetchedMessages.addAll(messageLoggerInterface.getLoggedIds(conversationIds.toTypedArray(), PREFETCH_MESSAGE_COUNT).toList())
        }.also { context.log.verbose("Loaded ${fetchedMessages.size} cached messages in ${it}ms") }
    }

    override fun init() {
        if (!isEnabled) return
        val keepMyOwnMessages = context.config.messaging.messageLogger.keepMyOwnMessages.get()
        val messageFilter by context.config.messaging.messageLogger.messageFilter

        context.event.subscribe(BuildMessageEvent::class, priority = 1) { event ->
            val messageInstance = event.message.instanceNonNull()
            if (event.message.messageState != MessageState.COMMITTED) return@subscribe

            cachedIdLinks[event.message.messageDescriptor!!.messageId!!] = event.message.orderKey!!
            val conversationId = event.message.messageDescriptor!!.conversationId.toString()
            //exclude messages sent by me
            if (!keepMyOwnMessages && event.message.senderId.toString() == context.database.myUserId) return@subscribe

            val uniqueMessageIdentifier = computeMessageIdentifier(conversationId, event.message.orderKey!!)
            val messageContentType = event.message.messageContent!!.contentType

            if (messageContentType != ContentType.STATUS) {
                if (messageFilter.isNotEmpty() && !messageFilter.contains(messageContentType?.name)) return@subscribe
                if (fetchedMessages.contains(uniqueMessageIdentifier)) return@subscribe
                fetchedMessages.add(uniqueMessageIdentifier)

                threadPool.execute {
                    try {
                        messageLoggerInterface.addMessage(conversationId, uniqueMessageIdentifier, context.gson.toJson(messageInstance).toByteArray(Charsets.UTF_8))
                    } catch (ignored: DeadObjectException) {}
                }

                return@subscribe
            }

            //query the deleted message
            val deletedMessageObject: JsonObject = if (deletedMessageCache.containsKey(uniqueMessageIdentifier))
                deletedMessageCache[uniqueMessageIdentifier]
            else {
                messageLoggerInterface.getMessage(conversationId, uniqueMessageIdentifier)?.let {
                    JsonParser.parseString(it.toString(Charsets.UTF_8)).asJsonObject
                }
            } ?: return@subscribe

            val messageJsonObject = deletedMessageObject.asJsonObject

            //if the message is a snap make it playable
            if (messageJsonObject["mMessageContent"]?.asJsonObject?.get("mContentType")?.asString == "SNAP") {
                messageJsonObject["mMetadata"].asJsonObject.addProperty("mPlayableSnapState", "PLAYABLE")
            }

            //serialize all properties of messageJsonObject and put in the message object
            messageInstance.javaClass.declaredFields.forEach { field ->
                field.isAccessible = true
                if (field.name == "mDescriptor") return@forEach // prevent the client message id from being overwritten
                messageJsonObject[field.name]?.let { fieldValue ->
                    field.set(messageInstance, context.gson.fromJson(fieldValue, field.type))
                }
            }

            deletedMessageCache[uniqueMessageIdentifier] = deletedMessageObject
        }
    }

    override fun onActivityCreate() {
        if (!isEnabled) return

        context.event.subscribe(BindViewEvent::class) { event ->
            event.chatMessage { conversationId, messageId ->
                event.view.removeForegroundDrawable("deletedMessage")
                makeUniqueIdentifier(conversationId, messageId.toLong())?.let { serverMessageId ->
                    if (!deletedMessageCache.contains(serverMessageId)) return@chatMessage
                } ?: return@chatMessage

                event.view.addForegroundDrawable("deletedMessage", ShapeDrawable(object: Shape() {
                    override fun draw(canvas: Canvas, paint: Paint) {
                        canvas.drawRect(0f, 0f, canvas.width.toFloat(), canvas.height.toFloat(), Paint().apply {
                            color = DELETED_MESSAGE_COLOR
                        })
                    }
                }))
            }
        }
    }
}
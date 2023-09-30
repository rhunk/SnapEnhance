package me.rhunk.snapenhance.features.impl.spying

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.drawable.ShapeDrawable
import android.graphics.drawable.shapes.Shape
import android.os.DeadObjectException
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import me.rhunk.snapenhance.core.event.events.impl.BindViewEvent
import me.rhunk.snapenhance.core.util.protobuf.ProtoReader
import me.rhunk.snapenhance.data.ContentType
import me.rhunk.snapenhance.data.MessageState
import me.rhunk.snapenhance.data.wrapper.impl.Message
import me.rhunk.snapenhance.features.Feature
import me.rhunk.snapenhance.features.FeatureLoadParams
import me.rhunk.snapenhance.hook.HookStage
import me.rhunk.snapenhance.hook.Hooker
import me.rhunk.snapenhance.ui.addForegroundDrawable
import me.rhunk.snapenhance.ui.removeForegroundDrawable
import java.util.concurrent.Executors
import kotlin.time.ExperimentalTime
import kotlin.time.measureTime

private fun Any.longHashCode(): Long {
    var h = 1125899906842597L
    val value = this.toString()
    for (element in value) h = 31 * h + element.code.toLong()
    return h
}

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

    val isEnabled get() = context.config.messaging.messageLogger.get()

    private val threadPool = Executors.newFixedThreadPool(10)

    private val cachedIdLinks = mutableMapOf<Long, Long>() // client id -> server id
    private val fetchedMessages = mutableListOf<Long>() // list of unique message ids
    private val deletedMessageCache = mutableMapOf<Long, JsonObject>() // unique message id -> message json object

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

    @OptIn(ExperimentalTime::class)
    override fun asyncOnActivityCreate() {
        if (!isEnabled || !context.database.hasArroyo()) {
            return
        }

        measureTime {
            val conversationIds = context.database.getFeedEntries(PREFETCH_FEED_COUNT).map { it.key!! }
            if (conversationIds.isEmpty()) return@measureTime
            fetchedMessages.addAll(messageLoggerInterface.getLoggedIds(conversationIds.toTypedArray(), PREFETCH_MESSAGE_COUNT).toList())
        }.also { context.log.verbose("Loaded ${fetchedMessages.size} cached messages in $it") }
    }

    private fun processSnapMessage(messageInstance: Any) {
        val message = Message(messageInstance)

        if (message.messageState != MessageState.COMMITTED) return

        cachedIdLinks[message.messageDescriptor.messageId] = message.orderKey
        val conversationId = message.messageDescriptor.conversationId.toString()
        //exclude messages sent by me
        if (message.senderId.toString() == context.database.myUserId) return

        val uniqueMessageIdentifier = computeMessageIdentifier(conversationId, message.orderKey)

        if (message.messageContent.contentType != ContentType.STATUS) {
            if (fetchedMessages.contains(uniqueMessageIdentifier)) return
            fetchedMessages.add(uniqueMessageIdentifier)

            threadPool.execute {
                try {
                    messageLoggerInterface.addMessage(conversationId, uniqueMessageIdentifier, context.gson.toJson(messageInstance).toByteArray(Charsets.UTF_8))
                } catch (ignored: DeadObjectException) {}
            }

            return
        }

        //query the deleted message
        val deletedMessageObject: JsonObject = if (deletedMessageCache.containsKey(uniqueMessageIdentifier))
            deletedMessageCache[uniqueMessageIdentifier]
        else {
            messageLoggerInterface.getMessage(conversationId, uniqueMessageIdentifier)?.let {
                JsonParser.parseString(it.toString(Charsets.UTF_8)).asJsonObject
            }
        } ?: return

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

    override fun init() {
        Hooker.hookConstructor(context.classCache.message, HookStage.AFTER, { isEnabled }) { param ->
            processSnapMessage(param.thisObject())
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
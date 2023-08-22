package me.rhunk.snapenhance.features.impl.spying

import android.os.DeadObjectException
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import me.rhunk.snapenhance.Logger
import me.rhunk.snapenhance.data.ContentType
import me.rhunk.snapenhance.data.MessageState
import me.rhunk.snapenhance.data.wrapper.impl.Message
import me.rhunk.snapenhance.features.Feature
import me.rhunk.snapenhance.features.FeatureLoadParams
import me.rhunk.snapenhance.hook.HookStage
import me.rhunk.snapenhance.hook.Hooker
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
        FeatureLoadParams.ACTIVITY_CREATE_ASYNC
) {
    companion object {
        const val PREFETCH_MESSAGE_COUNT = 20
        const val PREFETCH_FEED_COUNT = 20
    }

    private val threadPool = Executors.newFixedThreadPool(10)

    //two level of cache to avoid querying the database
    private val fetchedMessages = mutableListOf<Long>()
    private val deletedMessageCache = mutableMapOf<Long, JsonObject>()

    fun isMessageRemoved(conversationId: String, orderKey: Long) = deletedMessageCache.containsKey(computeMessageIdentifier(conversationId, orderKey))

    fun deleteMessage(conversationId: String, clientMessageId: Long) {
        val serverMessageId = getServerMessageIdentifier(conversationId, clientMessageId) ?: return
        fetchedMessages.remove(serverMessageId)
        deletedMessageCache.remove(serverMessageId)
        context.bridgeClient.deleteMessageLoggerMessage(conversationId, serverMessageId)
    }

    fun getMessageObject(conversationId: String, orderKey: Long): JsonObject? {
        val messageIdentifier = computeMessageIdentifier(conversationId, orderKey)
        if (deletedMessageCache.containsKey(messageIdentifier)) {
            return deletedMessageCache[messageIdentifier]
        }
        return context.bridgeClient.getMessageLoggerMessage(conversationId, messageIdentifier)?.let {
            JsonParser.parseString(it.toString(Charsets.UTF_8)).asJsonObject
        }
    }

    private fun computeMessageIdentifier(conversationId: String, orderKey: Long) = (orderKey.toString() + conversationId).longHashCode()
    private fun getServerMessageIdentifier(conversationId: String, clientMessageId: Long): Long? {
        val serverMessageId = context.database.getConversationMessageFromId(clientMessageId)?.serverMessageId?.toLong() ?: return null
        return computeMessageIdentifier(conversationId, serverMessageId)
    }

    @OptIn(ExperimentalTime::class)
    override fun asyncOnActivityCreate() {
        if (!context.database.hasArroyo()) {
            return
        }

        measureTime {
            context.database.getFeedEntries(PREFETCH_FEED_COUNT).forEach { friendFeedInfo ->
                fetchedMessages.addAll(context.bridgeClient.getLoggedMessageIds(friendFeedInfo.key!!, PREFETCH_MESSAGE_COUNT).toList())
            }
        }.also { Logger.debug("Loaded ${fetchedMessages.size} cached messages in $it") }
    }

    private fun processSnapMessage(messageInstance: Any) {
        val message = Message(messageInstance)

        if (message.messageState != MessageState.COMMITTED) return

        //exclude messages sent by me
        if (message.senderId.toString() == context.database.myUserId) return

        val conversationId = message.messageDescriptor.conversationId.toString()
        val serverIdentifier = computeMessageIdentifier(conversationId, message.orderKey)

        if (message.messageContent.contentType != ContentType.STATUS) {
            if (fetchedMessages.contains(serverIdentifier)) return
            fetchedMessages.add(serverIdentifier)

            threadPool.execute {
                try {
                    context.bridgeClient.getMessageLoggerMessage(conversationId, serverIdentifier)?.let {
                        return@execute
                    }
                    context.bridgeClient.addMessageLoggerMessage(conversationId, serverIdentifier, context.gson.toJson(messageInstance).toByteArray(Charsets.UTF_8))
                } catch (ignored: DeadObjectException) {}
            }

            return
        }

        //query the deleted message
        val deletedMessageObject: JsonObject = if (deletedMessageCache.containsKey(serverIdentifier))
            deletedMessageCache[serverIdentifier]
        else {
            context.bridgeClient.getMessageLoggerMessage(conversationId, serverIdentifier)?.let {
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
            messageJsonObject[field.name]?.let { fieldValue ->
                field.set(messageInstance, context.gson.fromJson(fieldValue, field.type))
            }
        }

        //set the message state to PREPARING for visibility
        with(message.messageContent.contentType) {
            if (this != ContentType.SNAP && this != ContentType.EXTERNAL_MEDIA) {
                message.messageState = MessageState.PREPARING
            }
        }

        deletedMessageCache[serverIdentifier] = deletedMessageObject
    }

    override fun init() {
        val messageLogger by context.config.messaging.messageLogger
        Hooker.hookConstructor(context.classCache.message, HookStage.AFTER, { messageLogger }) { param ->
            processSnapMessage(param.thisObject())
        }
    }
}
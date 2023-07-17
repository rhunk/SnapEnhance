package me.rhunk.snapenhance.features.impl.spying

import android.os.DeadObjectException
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import me.rhunk.snapenhance.Logger
import me.rhunk.snapenhance.config.ConfigProperty
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

    private val myUserId by lazy { context.database.getMyUserId() }

    fun isMessageRemoved(messageId: Long) = deletedMessageCache.containsKey(messageId)

    fun deleteMessage(conversationId: String, messageId: Long) {
        fetchedMessages.remove(messageId)
        deletedMessageCache.remove(messageId)
        context.bridgeClient.deleteMessageLoggerMessage(conversationId, messageId)
    }

    fun getMessageObject(conversationId: String, messageId: Long): JsonObject? {
        if (deletedMessageCache.containsKey(messageId)) {
            return deletedMessageCache[messageId]
        }
        return context.bridgeClient.getMessageLoggerMessage(conversationId, messageId)?.let {
            JsonParser.parseString(it.toString(Charsets.UTF_8)).asJsonObject
        }
    }

    @OptIn(ExperimentalTime::class)
    override fun asyncOnActivityCreate() {
        ConfigProperty.MESSAGE_LOGGER.valueContainer.addPropertyChangeListener {
            context.config.writeConfig()
            context.softRestartApp()
        }

        if (!context.database.hasArroyo()) {
            return
        }

        measureTime {
            context.database.getFriendFeed(PREFETCH_FEED_COUNT).forEach { friendFeedInfo ->
                fetchedMessages.addAll(context.bridgeClient.getLoggedMessageIds(friendFeedInfo.key!!, PREFETCH_MESSAGE_COUNT).toList())
            }
        }.also { Logger.debug("Loaded ${fetchedMessages.size} cached messages in $it") }
    }

    private fun processSnapMessage(messageInstance: Any) {
        val message = Message(messageInstance)

        if (message.messageState != MessageState.COMMITTED) return

        //exclude messages sent by me
        if (message.senderId.toString() == myUserId) return

        val messageId = message.messageDescriptor.messageId
        val conversationId = message.messageDescriptor.conversationId.toString()

        if (message.messageContent.contentType != ContentType.STATUS) {
            if (fetchedMessages.contains(messageId)) return
            fetchedMessages.add(messageId)

            threadPool.execute {
                try {
                    context.bridgeClient.getMessageLoggerMessage(conversationId, messageId)?.let {
                        return@execute
                    }
                    context.bridgeClient.addMessageLoggerMessage(conversationId, messageId, context.gson.toJson(messageInstance).toByteArray(Charsets.UTF_8))
                } catch (ignored: DeadObjectException) {}
            }

            return
        }

        //query the deleted message
        val deletedMessageObject: JsonObject = if (deletedMessageCache.containsKey(messageId))
            deletedMessageCache[messageId]
        else {
            context.bridgeClient.getMessageLoggerMessage(conversationId, messageId)?.let {
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

        deletedMessageCache[messageId] = deletedMessageObject
    }

    override fun init() {
        Hooker.hookConstructor(context.classCache.message, HookStage.AFTER, {
            context.config.bool(ConfigProperty.MESSAGE_LOGGER)
        }) { param ->
            processSnapMessage(param.thisObject())
        }
    }
}
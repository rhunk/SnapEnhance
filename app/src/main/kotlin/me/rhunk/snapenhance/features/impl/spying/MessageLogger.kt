package me.rhunk.snapenhance.features.impl.spying

import com.google.gson.JsonParser
import me.rhunk.snapenhance.config.ConfigProperty
import me.rhunk.snapenhance.data.ContentType
import me.rhunk.snapenhance.data.MessageState
import me.rhunk.snapenhance.data.wrapper.impl.Message
import me.rhunk.snapenhance.features.Feature
import me.rhunk.snapenhance.features.FeatureLoadParams
import me.rhunk.snapenhance.hook.HookStage
import me.rhunk.snapenhance.hook.Hooker

class MessageLogger : Feature("MessageLogger", loadParams = FeatureLoadParams.INIT_SYNC or FeatureLoadParams.ACTIVITY_CREATE_SYNC) {
    private val messageCache = mutableMapOf<Long, String>()
    private val removedMessages = linkedSetOf<Long>()
    private val myUserId by lazy { context.database.getMyUserId() }

    fun isMessageRemoved(messageId: Long) = removedMessages.contains(messageId)

    override fun onActivityCreate() {
        if (!context.database.hasArroyo()) {
            context.bridgeClient.clearMessageLogger()
        }
    }

    //FIXME: message disappears when the conversation is set to delete on view
    override fun init() {
        Hooker.hookConstructor(context.classCache.message, HookStage.AFTER, {
            context.config.bool(ConfigProperty.MESSAGE_LOGGER)
        }) {
            val message = Message(it.thisObject())
            val messageId = message.messageDescriptor.messageId
            val contentType = message.messageContent.contentType
            val messageState = message.messageState

            if (messageState != MessageState.COMMITTED) return@hookConstructor


            if (contentType == ContentType.STATUS) {
                //query the deleted message
                val deletedMessage: String = if (messageCache.containsKey(messageId)) messageCache[messageId] else {
                    context.bridgeClient.getMessageLoggerMessage(messageId)?.toString(Charsets.UTF_8)
                } ?: return@hookConstructor

                val messageJsonObject = JsonParser.parseString(deletedMessage).asJsonObject

                //if the message is a snap make it playable
                if (messageJsonObject["mMessageContent"]?.asJsonObject?.get("mContentType")?.asString == "SNAP") {
                    messageJsonObject["mMetadata"].asJsonObject.addProperty("mPlayableSnapState", "PLAYABLE")
                }

                //serialize all properties of messageJsonObject and put in the message object
                message.instanceNonNull().javaClass.declaredFields.forEach { field ->
                    field.isAccessible = true
                    val fieldName = field.name
                    val fieldValue = messageJsonObject[fieldName]
                    if (fieldValue != null) {
                        field.set(message.instanceNonNull(), context.gson.fromJson(fieldValue, field.type))
                    }
                }

                //set the message state to PREPARING for visibility
                if (message.messageContent.contentType != ContentType.SNAP && message.messageContent.contentType != ContentType.EXTERNAL_MEDIA) {
                    message.messageState = MessageState.PREPARING
                }
                removedMessages.add(messageId)
                return@hookConstructor
            }

            //exclude messages sent by me
            if (message.senderId.toString() == myUserId) return@hookConstructor

            if (!messageCache.containsKey(messageId)) {
                context.executeAsync {
                    val storedMessage = context.bridgeClient.getMessageLoggerMessage(messageId)?.toString(Charsets.UTF_8)
                    if (storedMessage == null) {
                        messageCache[messageId] = context.gson.toJson(message.instanceNonNull())
                        context.bridgeClient.addMessageLoggerMessage(messageId, messageCache[messageId]!!.toByteArray(Charsets.UTF_8))
                        return@executeAsync
                    }
                    messageCache[messageId] = storedMessage
                }
            }
        }
    }
}
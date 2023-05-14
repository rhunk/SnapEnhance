package me.rhunk.snapenhance.features.impl.spy

import com.google.gson.JsonParser
import me.rhunk.snapenhance.config.ConfigProperty
import me.rhunk.snapenhance.data.ContentType
import me.rhunk.snapenhance.data.MessageState
import me.rhunk.snapenhance.features.Feature
import me.rhunk.snapenhance.features.FeatureLoadParams
import me.rhunk.snapenhance.hook.HookStage
import me.rhunk.snapenhance.hook.Hooker
import me.rhunk.snapenhance.util.getObjectField

class MessageLogger : Feature("MessageLogger", loadParams = FeatureLoadParams.INIT_SYNC) {
    private val messageCache = mutableMapOf<Long, String>()
    private val removedMessages = linkedSetOf<Long>()

    fun isMessageRemoved(messageId: Long) = removedMessages.contains(messageId)

    override fun init() {
        Hooker.hookConstructor(context.classCache.message, HookStage.AFTER, {
            context.config.bool(ConfigProperty.MESSAGE_LOGGER)
        }) {
            val message = it.thisObject<Any>()
            val messageId = message.getObjectField("mDescriptor").getObjectField("mMessageId") as Long
            val contentType = ContentType.valueOf(message.getObjectField("mMessageContent").getObjectField("mContentType").toString())
            val messageState = MessageState.valueOf(message.getObjectField("mState").toString())

            if (messageState != MessageState.COMMITTED) return@hookConstructor

            if (contentType == ContentType.STATUS) {
                //query the deleted message
                val deletedMessage: String = if (messageCache.containsKey(messageId)) messageCache[messageId] else {
                    context.bridgeClient.getMessageLoggerMessage(messageId)?.toString(Charsets.UTF_8)
                } ?: return@hookConstructor

                val messageJsonObject = JsonParser.parseString(deletedMessage).asJsonObject

                //if the message is a snap make it playable
                if (messageJsonObject["mMessageContent"].asJsonObject["mContentType"].asString == "SNAP") {
                    messageJsonObject["mMetadata"].asJsonObject.addProperty("mPlayableSnapState", "PLAYABLE")
                }

                //serialize all properties of messageJsonObject and put in the message object
                message.javaClass.declaredFields.forEach { field ->
                    field.isAccessible = true
                    val fieldName = field.name
                    val fieldValue = messageJsonObject[fieldName]
                    if (fieldValue != null) {
                        field.set(message, context.gson.fromJson(fieldValue, field.type))
                    }
                }

                removedMessages.add(messageId)
                return@hookConstructor
            }

            if (!messageCache.containsKey(messageId)) {
                val serializedMessage = context.gson.toJson(message)
                messageCache[messageId] = serializedMessage
                context.bridgeClient.addMessageLoggerMessage(messageId, serializedMessage.toByteArray(Charsets.UTF_8))
            }
        }
    }
}
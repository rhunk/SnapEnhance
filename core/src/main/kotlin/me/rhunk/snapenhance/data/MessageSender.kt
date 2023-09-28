package me.rhunk.snapenhance.data

import me.rhunk.snapenhance.ModContext
import me.rhunk.snapenhance.core.util.CallbackBuilder
import me.rhunk.snapenhance.core.util.protobuf.ProtoWriter
import me.rhunk.snapenhance.data.wrapper.AbstractWrapper
import me.rhunk.snapenhance.data.wrapper.impl.MessageDestinations
import me.rhunk.snapenhance.data.wrapper.impl.SnapUUID
import me.rhunk.snapenhance.features.impl.Messaging

class MessageSender(
    private val context: ModContext,
) {
    companion object {
        val redSnapProto: (ByteArray?) -> ByteArray = { extras ->
            ProtoWriter().apply {
                from(11) {
                    from(5) {
                        from(1) {
                            from(1) {
                                addVarInt(2, 0)
                                addVarInt(12, 0)
                                addVarInt(15, 0)
                            }
                            addVarInt(6, 0)
                        }
                        from(2) {
                            addVarInt(5, 1) // audio by default
                            addBuffer(6, byteArrayOf())
                        }
                    }
                    extras?.let {
                        addBuffer(13, it)
                    }
                }
            }.toByteArray()
        }

        val audioNoteProto: (Long) -> ByteArray = { duration ->
            ProtoWriter().apply {
                from(6, 1) {
                    from(1) {
                        addVarInt(2, 4)
                        from(5) {
                            addVarInt(1, 0)
                            addVarInt(2, 0)
                        }
                        addVarInt(7, 0)
                        addVarInt(13, duration)
                    }
                }
            }.toByteArray()
        }

    }

    private val sendMessageCallback by lazy { context.mappings.getMappedClass("callbacks", "SendMessageCallback") }

    private val platformAnalyticsCreatorClass by lazy {
        context.mappings.getMappedClass("PlatformAnalyticsCreator")
    }

    private fun defaultPlatformAnalytics(): ByteArray {
        val analyticsSource = platformAnalyticsCreatorClass.constructors[0].parameterTypes[0]
        val chatAnalyticsSource = analyticsSource.enumConstants.first { it.toString() == "CHAT" }

        val platformAnalyticsDefaultArgs = arrayOf(chatAnalyticsSource, null, null, null, null, null, null, null, null, null, 0L, 0L,
            null, null, false, null, null, 0L, null, null, false, null, null,
            null, null, null, null, null, null, null, null, null, null, null,
            null, null, null, null, null, null, false, null, null, false, 0L, -2, 8191)

        val platformAnalyticsInstance = platformAnalyticsCreatorClass.constructors[0].newInstance(
            *platformAnalyticsDefaultArgs
        ) ?: throw Exception("Failed to create platform analytics instance")

        return platformAnalyticsInstance.javaClass.declaredMethods.first { it.returnType == ByteArray::class.java }
            .invoke(platformAnalyticsInstance) as ByteArray?
            ?: throw Exception("Failed to get platform analytics content")
    }

    private fun createLocalMessageContentTemplate(
        contentType: ContentType,
        messageContent: ByteArray,
        localMediaReference: ByteArray? = null,
        metricMessageMediaType: MetricsMessageMediaType = MetricsMessageMediaType.DERIVED_FROM_MESSAGE_TYPE,
        metricsMediaType: MetricsMessageType = MetricsMessageType.TEXT,
        savePolicy: String = "PROHIBITED",
    ): String {
        return """
        {
            "mAllowsTranscription": false,
            "mBotMention": false,
            "mContent": [${messageContent.joinToString(",")}],
            "mContentType": "${contentType.name}",
            "mIncidentalAttachments": [],
            "mLocalMediaReferences": [${
                if (localMediaReference != null) {
                    "{\"mId\": [${localMediaReference.joinToString(",")}]}"
                } else {
                    ""
                }
            }],
            "mPlatformAnalytics": {
                "mAttemptId": {
                  "mId": [${(1..16).map { (-127 ..127).random() }.joinToString(",")}]
                },
                "mContent": [${defaultPlatformAnalytics().joinToString(",")}],
                "mMetricsMessageMediaType": "${metricMessageMediaType.name}",
                "mMetricsMessageType": "${metricsMediaType.name}",
                "mReactionSource": "NONE"
            },
            "mSavePolicy": "$savePolicy"
        }
        """.trimIndent()
    }

    private fun internalSendMessage(conversations: List<SnapUUID>, localMessageContentTemplate: String, callback: Any) {
        val sendMessageWithContentMethod = context.classCache.conversationManager.declaredMethods.first { it.name == "sendMessageWithContent" }

        val localMessageContent = context.gson.fromJson(localMessageContentTemplate, context.classCache.localMessageContent)
        val messageDestinations = MessageDestinations(AbstractWrapper.newEmptyInstance(context.classCache.messageDestinations)).also {
            it.conversations = conversations
            it.mPhoneNumbers = arrayListOf()
            it.stories = arrayListOf()
        }

        sendMessageWithContentMethod.invoke(context.feature(Messaging::class).conversationManager, messageDestinations.instanceNonNull(), localMessageContent, callback)
    }

    //TODO: implement sendSnapMessage
    /*
    fun sendSnapMessage(conversations: List<SnapUUID>, chatMediaType: ChatMediaType, uri: Uri, onError: (Any) -> Unit = {}, onSuccess: () -> Unit = {}) {
        val mediaReferenceBuffer = FlatBufferBuilder(0).apply {
            val uriOffset = createString(uri.toString())
            forceDefaults(true)
            startTable(2)
            addOffset(1, uriOffset, 0)
            addInt(0, chatMediaType.value, 0)
            finish(endTable())
            finished()
        }.sizedByteArray()

        internalSendMessage(conversations, createLocalMessageContentTemplate(
            contentType = ContentType.SNAP,
            messageContent = redSnapProto(chatMediaType == ChatMediaType.AUDIO || chatMediaType == ChatMediaType.VIDEO),
            localMediaReference = mediaReferenceBuffer,
            metricMessageMediaType = MetricsMessageMediaType.IMAGE,
            metricsMediaType = MetricsMessageType.SNAP
        ), CallbackBuilder(sendMessageCallback)
            .override("onSuccess") {
                onSuccess()
            }
            .override("onError") {
                onError(it.arg(0))
            }
            .build())
    }*/

    fun sendChatMessage(conversations: List<SnapUUID>, message: String, onError: (Any) -> Unit = {}, onSuccess: () -> Unit = {}) {
        internalSendMessage(conversations, createLocalMessageContentTemplate(ContentType.CHAT, ProtoWriter().apply {
            from(2) {
                addString(1, message)
            }
        }.toByteArray(), savePolicy = "LIFETIME"), CallbackBuilder(sendMessageCallback)
            .override("onSuccess", callback = { onSuccess() })
            .override("onError", callback = { onError(it.arg(0)) })
            .build())
    }

    fun sendCustomChatMessage(conversations: List<SnapUUID>, contentType: ContentType,  message: ProtoWriter.() -> Unit, onError: (Any) -> Unit = {}, onSuccess: () -> Unit = {}) {
        internalSendMessage(conversations, createLocalMessageContentTemplate(contentType, ProtoWriter().apply {
            message()
        }.toByteArray(), savePolicy = "LIFETIME"), CallbackBuilder(sendMessageCallback)
            .override("onSuccess", callback = { onSuccess() })
            .override("onError", callback = { onError(it.arg(0)) })
            .build())
    }
}
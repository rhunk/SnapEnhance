package me.rhunk.snapenhance.data

import me.rhunk.snapenhance.ModContext
import me.rhunk.snapenhance.data.wrapper.AbstractWrapper
import me.rhunk.snapenhance.data.wrapper.impl.MessageDestinations
import me.rhunk.snapenhance.data.wrapper.impl.SnapUUID
import me.rhunk.snapenhance.features.impl.Messaging
import me.rhunk.snapenhance.util.CallbackBuilder
import me.rhunk.snapenhance.util.protobuf.ProtoWriter

class MessageSender(
    private val context: ModContext,
) {
    companion object {
        val redSnapProto: (Boolean) -> ByteArray = {hasAudio ->
            ProtoWriter().apply {
                write(11, 5) {
                    write(1) {
                        write(1) {
                            writeConstant(2, 0)
                            writeConstant(12, 0)
                            writeConstant(15, 0)
                        }
                        writeConstant(6, 0)
                    }
                    write(2) {
                        writeConstant(5, if (hasAudio) 1 else 0)
                        writeBuffer(6, byteArrayOf())
                    }
                }
            }.toByteArray()
        }

        val audioNoteProto: (Long) -> ByteArray = { duration ->
            ProtoWriter().apply {
                write(6, 1) {
                    write(1) {
                        writeConstant(2, 4)
                        write(5) {
                            writeConstant(1, 0)
                            writeConstant(2, 0)
                        }
                        writeConstant(7, 0)
                        writeConstant(13, duration)
                    }
                }
            }.toByteArray()
        }

    }

    private val sendMessageCallback by lazy { context.mappings.getMappedClass("callbacks", "SendMessageCallback") }

    private fun createLocalMessageContentTemplate(
        contentType: ContentType,
        messageContent: ByteArray,
        localMediaReference: ByteArray? = null,
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
                "mAttemptId": null,
                "mContent": [],
                "mMetricsMessageMediaType": "NO_MEDIA",
                "mMetricsMessageType": "TEXT",
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

    fun sendChatMessage(conversations: List<SnapUUID>, message: String, onError: (Any) -> Unit = {}, onSuccess: () -> Unit = {}) {
        internalSendMessage(conversations, createLocalMessageContentTemplate(ContentType.CHAT, ProtoWriter().apply {
            write(2) {
                writeString(1, message)
            }
        }.toByteArray(), savePolicy = "LIFETIME"), CallbackBuilder(sendMessageCallback)
            .override("onSuccess", callback = { onSuccess() })
            .override("onError", callback = { onError(it.arg(0)) })
            .build())
    }
}
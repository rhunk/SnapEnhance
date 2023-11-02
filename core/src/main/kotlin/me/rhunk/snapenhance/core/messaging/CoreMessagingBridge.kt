package me.rhunk.snapenhance.core.messaging

import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.suspendCancellableCoroutine
import me.rhunk.snapenhance.bridge.snapclient.MessagingBridge
import me.rhunk.snapenhance.bridge.snapclient.SessionStartListener
import me.rhunk.snapenhance.bridge.snapclient.types.Message
import me.rhunk.snapenhance.common.data.MessageUpdate
import me.rhunk.snapenhance.core.ModContext
import me.rhunk.snapenhance.core.features.impl.downloader.decoder.MessageDecoder
import me.rhunk.snapenhance.core.features.impl.messaging.Messaging


fun me.rhunk.snapenhance.core.wrapper.impl.Message.toBridge(): Message {
    return Message().also { output ->
        output.conversationId = this.messageDescriptor!!.conversationId.toString()
        output.senderId = this.senderId.toString()
        output.clientMessageId = this.messageDescriptor!!.messageId!!
        output.serverMessageId = this.orderKey!!
        output.contentType = this.messageContent?.contentType?.id ?: -1
        output.content = this.messageContent?.content
        output.mediaReferences = MessageDecoder.getEncodedMediaReferences(this.messageContent!!)
    }
}


class CoreMessagingBridge(
    private val context: ModContext
) : MessagingBridge.Stub() {
    private val conversationManager get() = context.feature(Messaging::class).conversationManager
    private var sessionStartListener: SessionStartListener? = null

    fun triggerSessionStart() {
        sessionStartListener?.onConnected()
        sessionStartListener = null
    }

    override fun isSessionStarted() = conversationManager != null
    override fun registerSessionStartListener(listener: SessionStartListener) {
        sessionStartListener = listener
    }

    override fun getMyUserId() = context.database.myUserId

    override fun fetchMessage(conversationId: String, clientMessageId: String): Message? {
        return runBlocking {
            suspendCancellableCoroutine { continuation ->
                conversationManager?.fetchMessage(
                    conversationId,
                    clientMessageId.toLong(),
                    onSuccess = {
                        continuation.resumeWith(Result.success(it.toBridge()))
                    },
                    onError = { continuation.resumeWith(Result.success(null)) }
                ) ?: continuation.resumeWith(Result.success(null))
            }
        }
    }

    override fun fetchMessageByServerId(
        conversationId: String,
        serverMessageId: String
    ): Message? {
        return runBlocking {
            suspendCancellableCoroutine { continuation ->
                conversationManager?.fetchMessageByServerId(
                    conversationId,
                    serverMessageId,
                    onSuccess = {
                        continuation.resumeWith(Result.success(it.toBridge()))
                    },
                    onError = { continuation.resumeWith(Result.success(null)) }
                ) ?: continuation.resumeWith(Result.success(null))
            }
        }
    }

    override fun fetchConversationWithMessagesPaginated(
        conversationId: String,
        limit: Int,
        beforeMessageId: Long
    ): List<Message>? {
        return runBlocking {
            suspendCancellableCoroutine { continuation ->
                conversationManager?.fetchConversationWithMessagesPaginated(
                    conversationId,
                    beforeMessageId,
                    limit,
                    onSuccess = { messages ->
                        continuation.resumeWith(Result.success(messages.map { it.toBridge() }))
                    },
                    onError = {
                        continuation.resumeWith(Result.success(null))
                    }
                ) ?: continuation.resumeWith(Result.success(null))
            }
        }
    }

    override fun updateMessage(
        conversationId: String,
        clientMessageId: Long,
        messageUpdate: String
    ): String? {
        return runBlocking {
            suspendCancellableCoroutine { continuation ->
                conversationManager?.updateMessage(
                    conversationId,
                    clientMessageId,
                    MessageUpdate.valueOf(messageUpdate),
                    onResult = {
                        continuation.resumeWith(Result.success(it))
                    }
                ) ?: continuation.resumeWith(Result.success("ConversationManager is null"))
            }
        }
    }

    override fun getOneToOneConversationId(userId: String) = context.database.getConversationLinkFromUserId(userId)?.clientConversationId
}
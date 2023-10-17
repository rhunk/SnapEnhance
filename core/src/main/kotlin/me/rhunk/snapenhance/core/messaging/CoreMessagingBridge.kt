package me.rhunk.snapenhance.core.messaging

import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.suspendCancellableCoroutine
import me.rhunk.snapenhance.bridge.snapclient.MessagingBridge
import me.rhunk.snapenhance.bridge.snapclient.types.Message
import me.rhunk.snapenhance.core.ModContext
import me.rhunk.snapenhance.core.features.impl.downloader.decoder.MessageDecoder
import me.rhunk.snapenhance.core.features.impl.messaging.Messaging
import me.rhunk.snapenhance.core.util.CallbackBuilder
import me.rhunk.snapenhance.core.wrapper.impl.SnapUUID


fun me.rhunk.snapenhance.core.wrapper.impl.Message.toBridge(): Message {
    return Message().also { output ->
        output.conversationId = this.messageDescriptor.conversationId.toString()
        output.senderId = this.senderId.toString()
        output.clientMessageId = this.messageDescriptor.messageId
        output.serverMessageId = this.orderKey
        output.contentType = this.messageContent.contentType?.id ?: -1
        output.content = this.messageContent.content
        output.mediaReferences = MessageDecoder.getEncodedMediaReferences(this.messageContent)
    }
}


class CoreMessagingBridge(
    private val context: ModContext
) : MessagingBridge.Stub() {
    private val conversationManager get() = context.feature(Messaging::class).conversationManager
    override fun getMyUserId() = context.database.myUserId

    override fun fetchMessage(conversationId: String, clientMessageId: String): Message? {
        return runBlocking {
            suspendCancellableCoroutine { continuation ->
                val callback = CallbackBuilder(
                    context.mappings.getMappedClass("callbacks", "FetchMessageCallback")
                ).override("onFetchMessageComplete") { param ->
                    val message = me.rhunk.snapenhance.core.wrapper.impl.Message(param.arg(0)).toBridge()
                    continuation.resumeWith(Result.success(message))
                }
                .override("onServerRequest", shouldUnhook = false) {}
                .override("onError") {
                    continuation.resumeWith(Result.success(null))
                }.build()

                context.classCache.conversationManager.methods.first { it.name == "fetchMessage" }.invoke(
                    conversationManager,
                    SnapUUID.fromString(conversationId).instanceNonNull(),
                    clientMessageId.toLong(),
                    callback
                )
            }
        }
    }

    override fun fetchMessageByServerId(
        conversationId: String,
        serverMessageId: String
    ): Message? {
        return runBlocking {
            suspendCancellableCoroutine { continuation ->
                val callback = CallbackBuilder(
                    context.mappings.getMappedClass("callbacks", "FetchMessageCallback")
                ).override("onFetchMessageComplete") { param ->
                    val message = me.rhunk.snapenhance.core.wrapper.impl.Message(param.arg(1)).toBridge()
                    continuation.resumeWith(Result.success(message))
                }
                .override("onServerRequest", shouldUnhook = false) {}
                .override("onError") {
                    continuation.resumeWith(Result.success(null))
                }.build()

                val serverMessageIdentifier = context.androidContext.classLoader.loadClass("com.snapchat.client.messaging.ServerMessageIdentifier")
                    .getConstructor(context.classCache.snapUUID, Long::class.javaPrimitiveType)
                    .newInstance(SnapUUID.fromString(conversationId).instanceNonNull(), serverMessageId.toLong())

                context.classCache.conversationManager.methods.first { it.name == "fetchMessageByServerId" }.invoke(
                    conversationManager,
                    serverMessageIdentifier,
                    callback
                )
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
                val callback = CallbackBuilder(
                    context.mappings.getMappedClass("callbacks", "FetchConversationWithMessagesCallback")
                ).override("onFetchConversationWithMessagesComplete") { param ->
                    val messagesList = param.arg<List<*>>(1).map {
                        me.rhunk.snapenhance.core.wrapper.impl.Message(it).toBridge()
                    }
                    continuation.resumeWith(Result.success(messagesList))
                }
                .override("onServerRequest", shouldUnhook = false) {}
                .override("onError") {
                    continuation.resumeWith(Result.success(null))
                }.build()

                context.classCache.conversationManager.methods.first { it.name == "fetchConversationWithMessagesPaginated" }.invoke(
                    conversationManager,
                    SnapUUID.fromString(conversationId).instanceNonNull(),
                    beforeMessageId,
                    limit,
                    callback
                )
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
                val callback = CallbackBuilder(
                    context.mappings.getMappedClass("callbacks", "Callback")
                ).override("onSuccess") {
                    continuation.resumeWith(Result.success(null))
                }
                .override("onError") {
                    continuation.resumeWith(Result.success(it.arg<Any>(0).toString()))
                }.build()

                context.classCache.conversationManager.methods.first { it.name == "updateMessage" }.invoke(
                    conversationManager,
                    SnapUUID.fromString(conversationId).instanceNonNull(),
                    clientMessageId,
                    context.classCache.messageUpdateEnum.enumConstants.first { it.toString() == messageUpdate },
                    callback
                )
            }
        }
    }

    override fun getOneToOneConversationId(userId: String) = context.database.getConversationLinkFromUserId(userId)?.clientConversationId
}
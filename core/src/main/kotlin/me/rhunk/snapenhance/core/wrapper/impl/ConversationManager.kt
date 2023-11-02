package me.rhunk.snapenhance.core.wrapper.impl

import me.rhunk.snapenhance.common.data.MessageUpdate
import me.rhunk.snapenhance.core.ModContext
import me.rhunk.snapenhance.core.util.CallbackBuilder
import me.rhunk.snapenhance.core.wrapper.AbstractWrapper

typealias CallbackResult = (error: String?) -> Unit

class ConversationManager(val context: ModContext, obj: Any) : AbstractWrapper(obj) {
    private fun findMethodByName(name: String) = context.classCache.conversationManager.declaredMethods.find { it.name == name } ?: throw RuntimeException("Could not find method $name")

    private val updateMessageMethod by lazy { findMethodByName("updateMessage") }
    private val fetchConversationWithMessagesPaginatedMethod by lazy { findMethodByName("fetchConversationWithMessagesPaginated") }
    private val fetchConversationWithMessagesMethod by lazy { findMethodByName("fetchConversationWithMessages") }
    private val fetchMessageByServerId by lazy { findMethodByName("fetchMessageByServerId") }
    private val displayedMessagesMethod by lazy { findMethodByName("displayedMessages") }
    private val fetchMessage by lazy { findMethodByName("fetchMessage") }


    fun updateMessage(conversationId: String, messageId: Long, action: MessageUpdate, onResult: CallbackResult = {}) {
        updateMessageMethod.invoke(
            instanceNonNull(),
            SnapUUID.fromString(conversationId).instanceNonNull(),
            messageId,
            context.classCache.messageUpdateEnum.enumConstants.first { it.toString() == action.toString() },
            CallbackBuilder(context.mappings.getMappedClass("callbacks", "Callback"))
                .override("onSuccess") { onResult(null) }
                .override("onError") { onResult(it.arg<Any>(0).toString()) }.build()
        )
    }

    fun fetchConversationWithMessagesPaginated(conversationId: String, lastMessageId: Long, amount: Int, onSuccess: (message: List<Message>) -> Unit, onError: (error: String) -> Unit) {
        val callback = CallbackBuilder(context.mappings.getMappedClass("callbacks", "FetchConversationWithMessagesCallback"))
            .override("onFetchConversationWithMessagesComplete") { param ->
                onSuccess(param.arg<List<*>>(1).map { Message(it) })
            }
            .override("onServerRequest", shouldUnhook = false) {}
            .override("onError") {
                onError(it.arg<Any>(0).toString())
            }.build()
        fetchConversationWithMessagesPaginatedMethod.invoke(instanceNonNull(), conversationId.toSnapUUID().instanceNonNull(), lastMessageId, amount, callback)
    }

    fun fetchConversationWithMessages(conversationId: String, onSuccess: (List<Message>) -> Unit, onError: (error: String) -> Unit) {
        fetchConversationWithMessagesMethod.invoke(
            instanceNonNull(),
            conversationId.toSnapUUID().instanceNonNull(),
            CallbackBuilder(context.mappings.getMappedClass("callbacks", "FetchConversationWithMessagesCallback"))
                .override("onFetchConversationWithMessagesComplete") { param ->
                    onSuccess(param.arg<List<*>>(1).map { Message(it) })
                }
                .override("onServerRequest", shouldUnhook = false) {}
                .override("onError") {
                    onError(it.arg<Any>(0).toString())
                }.build()
        )
    }

    fun displayedMessages(conversationId: String, messageId: Long, onResult: CallbackResult = {}) {
        displayedMessagesMethod.invoke(
            instanceNonNull(),
            conversationId.toSnapUUID(),
            messageId,
            CallbackBuilder(context.mappings.getMappedClass("callbacks", "Callback"))
                .override("onSuccess") { onResult(null) }
                .override("onError") { onResult(it.arg<Any>(0).toString()) }.build()
        )
    }

    fun fetchMessage(conversationId: String, messageId: Long, onSuccess: (Message) -> Unit, onError: (error: String) -> Unit = {}) {
        fetchMessage.invoke(
            instanceNonNull(),
            conversationId.toSnapUUID().instanceNonNull(),
            messageId,
            CallbackBuilder(context.mappings.getMappedClass("callbacks", "FetchMessageCallback"))
                .override("onFetchMessageComplete") { param ->
                    onSuccess(Message(param.arg(0)))
                }
                .override("onError") {
                    onError(it.arg<Any>(0).toString())
                }.build()
        )
    }

    fun fetchMessageByServerId(conversationId: String, serverMessageId: String, onSuccess: (Message) -> Unit, onError: (error: String) -> Unit) {
        val serverMessageIdentifier = context.classCache.serverMessageIdentifier
            .getConstructor(context.classCache.snapUUID, Long::class.javaPrimitiveType)
            .newInstance(conversationId.toSnapUUID().instanceNonNull(), serverMessageId.toLong())

        fetchMessageByServerId.invoke(
            instanceNonNull(),
            serverMessageIdentifier,
            CallbackBuilder(context.mappings.getMappedClass("callbacks", "FetchMessageCallback"))
                .override("onFetchMessageComplete") { param ->
                    onSuccess(Message(param.arg(1)))
                }
                .override("onError") {
                    onError(it.arg<Any>(0).toString())
                }.build()
        )
    }
}
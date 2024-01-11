package me.rhunk.snapenhance.core.wrapper.impl

import me.rhunk.snapenhance.common.data.MessageUpdate
import me.rhunk.snapenhance.core.ModContext
import me.rhunk.snapenhance.core.util.CallbackBuilder
import me.rhunk.snapenhance.core.util.ktx.getObjectField
import me.rhunk.snapenhance.core.util.ktx.setObjectField
import me.rhunk.snapenhance.core.wrapper.AbstractWrapper
import me.rhunk.snapenhance.mapper.impl.CallbackMapper

typealias CallbackResult = (error: String?) -> Unit

class ConversationManager(
    val context: ModContext,
    obj: Any
) : AbstractWrapper(obj) {
    private fun findMethodByName(name: String) = context.classCache.conversationManager.declaredMethods.find { it.name == name } ?: throw RuntimeException("Could not find method $name")

    private val updateMessageMethod by lazy { findMethodByName("updateMessage") }
    private val fetchConversationWithMessagesPaginatedMethod by lazy { findMethodByName("fetchConversationWithMessagesPaginated") }
    private val fetchConversationWithMessagesMethod by lazy { findMethodByName("fetchConversationWithMessages") }
    private val fetchMessageByServerId by lazy { findMethodByName("fetchMessageByServerId") }
    private val fetchMessagesByServerIds by lazy { findMethodByName("fetchMessagesByServerIds") }
    private val displayedMessagesMethod by lazy { findMethodByName("displayedMessages") }
    private val fetchMessage by lazy { findMethodByName("fetchMessage") }
    private val clearConversation by lazy { findMethodByName("clearConversation") }
    private val getOneOnOneConversationIds by lazy { findMethodByName("getOneOnOneConversationIds") }


    private fun getCallbackClass(name: String): Class<*> {
        lateinit var result: Class<*>
        context.mappings.useMapper(CallbackMapper::class) {
            result = context.androidContext.classLoader.loadClass(callbacks.get()!![name])
        }
        return result
    }


    fun updateMessage(conversationId: String, messageId: Long, action: MessageUpdate, onResult: CallbackResult = {}) {
        updateMessageMethod.invoke(
            instanceNonNull(),
            SnapUUID.fromString(conversationId).instanceNonNull(),
            messageId,
            context.classCache.messageUpdateEnum.enumConstants.first { it.toString() == action.toString() },
            CallbackBuilder(getCallbackClass("Callback"))
                .override("onSuccess") { onResult(null) }
                .override("onError") { onResult(it.arg<Any>(0).toString()) }.build()
        )
    }

    fun fetchConversationWithMessagesPaginated(conversationId: String, lastMessageId: Long, amount: Int, onSuccess: (message: List<Message>) -> Unit, onError: (error: String) -> Unit) {
        val callback = CallbackBuilder(getCallbackClass("FetchConversationWithMessagesCallback"))
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
            CallbackBuilder(getCallbackClass("FetchConversationWithMessagesCallback"))
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
            conversationId.toSnapUUID().instanceNonNull(),
            messageId,
            CallbackBuilder(getCallbackClass("Callback"))
                .override("onSuccess") { onResult(null) }
                .override("onError") { onResult(it.arg<Any>(0).toString()) }.build()
        )
    }

    fun fetchMessage(conversationId: String, messageId: Long, onSuccess: (Message) -> Unit, onError: (error: String) -> Unit = {}) {
        fetchMessage.invoke(
            instanceNonNull(),
            conversationId.toSnapUUID().instanceNonNull(),
            messageId,
            CallbackBuilder(getCallbackClass("FetchMessageCallback"))
                .override("onFetchMessageComplete") { param ->
                    onSuccess(Message(param.arg(0)))
                }
                .override("onError") {
                    onError(it.arg<Any>(0).toString())
                }.build()
        )
    }

    fun fetchMessageByServerId(conversationId: String, serverMessageId: Long, onSuccess: (Message) -> Unit, onError: (error: String) -> Unit) {
        val serverMessageIdentifier = CallbackBuilder.createEmptyObject(context.classCache.serverMessageIdentifier.constructors.first())?.apply {
            setObjectField("mServerConversationId", conversationId.toSnapUUID().instanceNonNull())
            setObjectField("mServerMessageId", serverMessageId)
        }

        fetchMessageByServerId.invoke(
            instanceNonNull(),
            serverMessageIdentifier,
            CallbackBuilder(getCallbackClass("FetchMessageCallback"))
                .override("onFetchMessageComplete") { param ->
                    onSuccess(Message(param.arg(0)))
                }
                .override("onError") {
                    onError(it.arg<Any>(0).toString())
                }.build()
        )
    }

    fun fetchMessagesByServerIds(conversationId: String, serverMessageIds: List<Long>, onSuccess: (List<Message>) -> Unit, onError: (error: String) -> Unit) {
        fetchMessagesByServerIds.invoke(
            instanceNonNull(),
            serverMessageIds.map {
                CallbackBuilder.createEmptyObject(context.classCache.serverMessageIdentifier.constructors.first())?.apply {
                    setObjectField("mServerConversationId", conversationId.toSnapUUID().instanceNonNull())
                    setObjectField("mServerMessageId", it)
                }
            },
            CallbackBuilder(getCallbackClass("FetchMessagesByServerIdsCallback"))
                .override("onSuccess") { param ->
                    onSuccess(param.arg<List<*>>(0).mapNotNull {
                        Message(it?.getObjectField("mMessage") ?: return@mapNotNull null)
                    })
                }
                .override("onError") {
                    onError(it.arg<Any>(0).toString())
                }.build()
        )
    }

    fun clearConversation(conversationId: String, onSuccess: () -> Unit, onError: (error: String) -> Unit) {
        val callback = CallbackBuilder(getCallbackClass("Callback"))
            .override("onSuccess") { onSuccess() }
            .override("onError") { onError(it.arg<Any>(0).toString()) }.build()
        clearConversation.invoke(instanceNonNull(), conversationId.toSnapUUID().instanceNonNull(), callback)
    }

    fun getOneOnOneConversationIds(userIds: List<String>, onSuccess: (List<Pair<String, String>>) -> Unit, onError: (error: String) -> Unit) {
        val callback = CallbackBuilder(getCallbackClass("GetOneOnOneConversationIdsCallback"))
            .override("onSuccess") { param ->
                onSuccess(param.arg<ArrayList<*>>(0).map {
                    SnapUUID(it.getObjectField("mUserId")).toString() to SnapUUID(it.getObjectField("mConversationId")).toString()
                })
            }
            .override("onError") { onError(it.arg<Any>(0).toString()) }.build()
        getOneOnOneConversationIds.invoke(instanceNonNull(), userIds.map { it.toSnapUUID().instanceNonNull() }.toMutableList(), callback)
    }
}
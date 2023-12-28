package me.rhunk.snapenhance.core.scripting.impl

import me.rhunk.snapenhance.common.data.MessageUpdate
import me.rhunk.snapenhance.common.scripting.bindings.AbstractBinding
import me.rhunk.snapenhance.common.scripting.bindings.BindingSide
import me.rhunk.snapenhance.common.scripting.ktx.scriptableObject
import me.rhunk.snapenhance.core.ModContext
import me.rhunk.snapenhance.core.features.impl.messaging.Messaging
import me.rhunk.snapenhance.core.wrapper.impl.Message
import me.rhunk.snapenhance.core.wrapper.impl.SnapUUID
import me.rhunk.snapenhance.core.wrapper.impl.Snapchatter
import org.mozilla.javascript.Scriptable
import org.mozilla.javascript.annotations.JSFunction

@Suppress("unused")
class CoreMessaging(
    private val modContext: ModContext
) : AbstractBinding("messaging", BindingSide.CORE) {
    private val messaging by lazy { modContext.feature(Messaging::class) }
    private val conversationManager get() = messaging.conversationManager

    @JSFunction
    fun isPresent() = conversationManager != null

    @JSFunction
    fun newSnapUUID(uuid: String) = SnapUUID.fromString(uuid)

    @JSFunction
    fun updateMessage(
        conversationId: String,
        messageId: Number,
        action: String,
        callback: (error: String?) -> Unit
    ) {
        conversationManager?.updateMessage(conversationId, messageId.toLong(), MessageUpdate.entries.find { it.key == action }
            ?: throw RuntimeException("Could not find message update $action"),
            callback)
    }

    @JSFunction
    fun fetchConversationWithMessagesPaginated(
        conversationId: String,
        lastMessageId: Long,
        amount: Int,
        callback: (error: String?, message: List<Message>) -> Unit,
    ) {
        conversationManager?.fetchConversationWithMessagesPaginated(conversationId, lastMessageId, amount, onSuccess = {
            callback(null, it)
        }, onError = {
            callback(it, emptyList())
        })
    }

    @JSFunction
    fun fetchConversationWithMessages(
        conversationId: String,
        callback: (error: String?, List<Message>) -> Unit
    ) {
        conversationManager?.fetchConversationWithMessages(conversationId, onSuccess = {
            callback(null, it)
        }, onError = {
            callback(it, emptyList())
        })
    }

    @JSFunction
    fun fetchMessageByServerId(
        conversationId: String,
        serverId: Long,
        callback: (error: String?, message: Message?) -> Unit,
    ) {
        conversationManager?.fetchMessageByServerId(conversationId, serverId, onSuccess = {
            callback(null, it)
        }, onError = {
            callback(it, null)
        })
    }

    @JSFunction
    fun fetchMessagesByServerIds(
        conversationId: String,
        serverIds: List<Number>,
        callback: (error: String?, List<Message>) -> Unit
    ) {
        conversationManager?.fetchMessagesByServerIds(conversationId, serverIds.map {
            it.toLong()
        }, onSuccess = {
            callback(null, it)
        }, onError = {
            callback(it, emptyList())
        })
    }

    @JSFunction
    fun displayedMessages(
        conversationId: String,
        lastMessageId: Number,
        callback: (error: String?) -> Unit
    ) {
        conversationManager?.displayedMessages(conversationId, lastMessageId.toLong(), callback)
    }

    @JSFunction
    fun fetchMessage(
        conversationId: String,
        messageId: Number,
        callback: (error: String?, message: Message?) -> Unit
    ) {
        conversationManager?.fetchMessage(conversationId, messageId.toLong(), onSuccess = {
            callback(null, it)
        }, onError = { callback(it, null) })
    }

    @JSFunction
    fun clearConversation(
        conversationId: String,
        callback: (error: String?) -> Unit
    ) {
        conversationManager?.clearConversation(conversationId, onSuccess = {
            callback(null)
        }, onError = {
            callback(it)
        })
    }

    @JSFunction
    fun getOneOnOneConversationIds(userIds: List<String>, callback: (error: String?, List<Scriptable>) -> Unit) {
        conversationManager?.getOneOnOneConversationIds(userIds, onSuccess = {
            callback(null, it.map { (userId, conversationId) ->
                scriptableObject {
                    putConst("conversationId", this, conversationId)
                    putConst("userId", this, userId)
                }
            })
        }, onError = {
            callback(it, emptyList())
        })
    }

    @JSFunction
    fun sendChatMessage(
        conversationId: String,
        message: String,
        result: (error: String?) -> Unit
    ) {
        modContext.messageSender.sendChatMessage(listOf(SnapUUID.fromString(conversationId)), message, onSuccess = { result(null) }, onError = { result(it.toString()) })
    }

    @JSFunction
    fun fetchSnapchatterInfos(
        userIds: List<String>
    ): List<Snapchatter> {
        return messaging.fetchSnapchatterInfos(userIds = userIds)
    }

    override fun getObject() = this
}

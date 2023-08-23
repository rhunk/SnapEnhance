package me.rhunk.snapenhance.features.impl.tweaks

import me.rhunk.snapenhance.Logger
import me.rhunk.snapenhance.core.messaging.MessagingRuleType
import me.rhunk.snapenhance.data.MessageState
import me.rhunk.snapenhance.data.wrapper.impl.Message
import me.rhunk.snapenhance.data.wrapper.impl.SnapUUID
import me.rhunk.snapenhance.features.FeatureLoadParams
import me.rhunk.snapenhance.features.MessagingRuleFeature
import me.rhunk.snapenhance.features.impl.Messaging
import me.rhunk.snapenhance.features.impl.spying.MessageLogger
import me.rhunk.snapenhance.features.impl.spying.StealthMode
import me.rhunk.snapenhance.hook.HookStage
import me.rhunk.snapenhance.hook.Hooker
import me.rhunk.snapenhance.util.CallbackBuilder
import me.rhunk.snapenhance.util.ktx.getObjectField
import java.util.concurrent.Executors

class AutoSave : MessagingRuleFeature("Auto Save", MessagingRuleType.AUTO_SAVE, loadParams = FeatureLoadParams.ACTIVITY_CREATE_ASYNC) {
    private val asyncSaveExecutorService = Executors.newSingleThreadExecutor()

    private val messageLogger by lazy { context.feature(MessageLogger::class) }
    private val messaging by lazy { context.feature(Messaging::class) }

    private val fetchConversationWithMessagesCallbackClass by lazy {  context.mappings.getMappedClass("callbacks", "FetchConversationWithMessagesCallback") }
    private val callbackClass by lazy {  context.mappings.getMappedClass("callbacks", "Callback") }

    private val updateMessageMethod by lazy { context.classCache.conversationManager.methods.first { it.name == "updateMessage" } }
    private val fetchConversationWithMessagesPaginatedMethod by lazy {
        context.classCache.conversationManager.methods.first { it.name == "fetchConversationWithMessagesPaginated" }
    }

    private val autoSaveFilter by lazy {
        context.config.messaging.autoSaveMessagesInConversations.get()
    }

    private fun saveMessage(conversationId: SnapUUID, message: Message) {
        val messageId = message.messageDescriptor.messageId
        if (messageLogger.isMessageRemoved(conversationId.toString(), message.orderKey)) return
        if (message.messageState != MessageState.COMMITTED) return

        val callback = CallbackBuilder(callbackClass)
            .override("onError") {
                Logger.xposedLog("Error saving message $messageId")
            }.build()

        runCatching {
            updateMessageMethod.invoke(
                context.feature(Messaging::class).conversationManager,
                conversationId.instanceNonNull(),
                messageId,
                context.classCache.messageUpdateEnum.enumConstants.first { it.toString() == "SAVE" },
                callback
            )
        }.onFailure {
            Logger.xposedLog("Error saving message $messageId", it)
        }

        //delay between saves
        Thread.sleep(100L)
    }

    private fun canSaveMessage(message: Message): Boolean {
        if (message.messageMetadata.savedBy.any { uuid -> uuid.toString() == context.database.myUserId }) return false
        val contentType = message.messageContent.contentType.toString()

        return autoSaveFilter.any { it == contentType }
    }

    private fun canSave(): Boolean {
        if (autoSaveFilter.isEmpty()) return false

        with(context.feature(Messaging::class)) {
            if (openedConversationUUID == null) return@canSave false
            val conversation = openedConversationUUID.toString()
            if (context.feature(StealthMode::class).canUseRule(conversation)) return@canSave false
            if (!canUseRule(conversation)) return@canSave false
        }
        return true
    }

    override fun asyncOnActivityCreate() {
        //called when enter in a conversation (or when a message is sent)
        Hooker.hook(
            context.mappings.getMappedClass("callbacks", "FetchConversationWithMessagesCallback"),
            "onFetchConversationWithMessagesComplete",
            HookStage.BEFORE,
            { canSave() }
        ) { param ->
            val conversationId = SnapUUID(param.arg<Any>(0).getObjectField("mConversationId")!!)
            val messages = param.arg<List<Any>>(1).map { Message(it) }
            messages.forEach {
                if (!canSaveMessage(it)) return@forEach
                asyncSaveExecutorService.submit {
                    saveMessage(conversationId, it)
                }
            }
        }

        //called when a message is received
        Hooker.hook(
            context.mappings.getMappedClass("callbacks", "FetchMessageCallback"),
            "onFetchMessageComplete",
            HookStage.BEFORE,
            { canSave() }
        ) { param ->
            val message = Message(param.arg(0))
            if (!canSaveMessage(message)) return@hook
            val conversationId = message.messageDescriptor.conversationId

            asyncSaveExecutorService.submit {
                saveMessage(conversationId, message)
            }
        }

        Hooker.hook(
            context.mappings.getMappedClass("callbacks", "SendMessageCallback"),
            "onSuccess",
            HookStage.BEFORE,
            { canSave() }
        ) {
            val callback = CallbackBuilder(fetchConversationWithMessagesCallbackClass).build()
            runCatching {
                fetchConversationWithMessagesPaginatedMethod.invoke(
                    messaging.conversationManager, messaging.openedConversationUUID!!.instanceNonNull(),
                    Long.MAX_VALUE,
                    3,
                    callback
                )
            }.onFailure {
                Logger.xposedLog("failed to save message", it)
            }
        }

    }
}
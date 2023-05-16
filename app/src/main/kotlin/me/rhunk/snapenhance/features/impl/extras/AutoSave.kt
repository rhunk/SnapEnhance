package me.rhunk.snapenhance.features.impl.extras

import me.rhunk.snapenhance.Logger
import me.rhunk.snapenhance.config.ConfigProperty
import me.rhunk.snapenhance.data.ContentType
import me.rhunk.snapenhance.data.wrapper.impl.Message
import me.rhunk.snapenhance.data.wrapper.impl.SnapUUID
import me.rhunk.snapenhance.features.Feature
import me.rhunk.snapenhance.features.FeatureLoadParams
import me.rhunk.snapenhance.features.impl.Messaging
import me.rhunk.snapenhance.features.impl.spy.MessageLogger
import me.rhunk.snapenhance.features.impl.spy.StealthMode
import me.rhunk.snapenhance.hook.HookStage
import me.rhunk.snapenhance.hook.Hooker
import me.rhunk.snapenhance.util.CallbackBuilder
import me.rhunk.snapenhance.util.getObjectField
import java.util.concurrent.Executors

class AutoSave : Feature("Auto Save", loadParams = FeatureLoadParams.ACTIVITY_CREATE_ASYNC) {
    private val asyncSaveExecutorService = Executors.newSingleThreadExecutor()

    private val messageLogger by lazy { context.feature(MessageLogger::class) }
    private val messaging by lazy { context.feature(Messaging::class) }

    private val myUserId by lazy { context.database.getMyUserId() }

    private val fetchConversationWithMessagesCallbackClass by lazy {  context.mappings.getMappedClass("callbacks", "FetchConversationWithMessagesCallback") }
    private val callbackClass by lazy {  context.mappings.getMappedClass("callbacks", "Callback") }

    private val updateMessageMethod by lazy { context.classCache.conversationManager.methods.first { it.name == "updateMessage" } }
    private val fetchConversationWithMessagesPaginatedMethod by lazy {
        context.classCache.conversationManager.methods.first { it.name == "fetchConversationWithMessagesPaginated" }
    }

    private fun saveMessage(conversationId: SnapUUID, message: Message) {
        val messageId = message.messageDescriptor.messageId
        if (messageLogger.isMessageRemoved(messageId)) return

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
        if (message.messageMetadata.savedBy.any { uuid -> uuid.toString() == myUserId }) return false
        //only save chats
        with(message.messageContent.contentType) {
            if (this != ContentType.CHAT &&
                this != ContentType.NOTE &&
                this != ContentType.STICKER &&
                this != ContentType.EXTERNAL_MEDIA) return false
        }
        return true
    }

    private fun canSave(): Boolean {
        with(context.feature(Messaging::class)) {
            if (lastOpenedConversationUUID == null || context.feature(StealthMode::class).isStealth(lastOpenedConversationUUID.toString())) return@canSave false
        }
        return true
    }

    override fun asyncOnActivityCreate() {
        //called when enter in a conversation (or when a message is sent)
        Hooker.hook(
            context.mappings.getMappedClass("callbacks", "FetchConversationWithMessagesCallback"),
            "onFetchConversationWithMessagesComplete",
            HookStage.BEFORE,
            { context.config.bool(ConfigProperty.AUTO_SAVE) && canSave()}
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
            { context.config.bool(ConfigProperty.AUTO_SAVE) && canSave()}
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
            { context.config.bool(ConfigProperty.AUTO_SAVE) && canSave()}
        ) {
            val callback = CallbackBuilder(fetchConversationWithMessagesCallbackClass).build()
            runCatching {
                fetchConversationWithMessagesPaginatedMethod.invoke(
                    messaging.conversationManager, messaging.lastOpenedConversationUUID!!.instanceNonNull(),
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
package me.rhunk.snapenhance.features.impl.tweaks

import me.rhunk.snapenhance.Logger
import me.rhunk.snapenhance.config.ConfigProperty
import me.rhunk.snapenhance.data.MessageState
import me.rhunk.snapenhance.data.wrapper.impl.Message
import me.rhunk.snapenhance.data.wrapper.impl.SnapUUID
import me.rhunk.snapenhance.features.Feature
import me.rhunk.snapenhance.features.FeatureLoadParams
import me.rhunk.snapenhance.features.impl.Messaging
import me.rhunk.snapenhance.features.impl.spying.MessageLogger
import me.rhunk.snapenhance.features.impl.spying.StealthMode
import me.rhunk.snapenhance.hook.HookStage
import me.rhunk.snapenhance.hook.Hooker
import me.rhunk.snapenhance.util.CallbackBuilder
import me.rhunk.snapenhance.util.getObjectField
import java.util.concurrent.Executors

class ChatPurge : Feature("ChatPurge", loadParams = FeatureLoadParams.ACTIVITY_CREATE_ASYNC) {
    private val asyncSaveExecutorService = Executors.newSingleThreadExecutor()

    private val messageLogger by lazy { context.feature(MessageLogger::class) }

    private val myUserId by lazy { context.database.getMyUserId() }

    private val callbackClass by lazy {  context.mappings.getMappedClass("callbacks", "Callback") }

    private val updateMessageMethod by lazy { context.classCache.conversationManager.methods.first { it.name == "updateMessage" } }

    private fun deleteMessage(conversationId: SnapUUID, message: Message) {
        val messageId = message.messageDescriptor.messageId
        if (messageLogger.isMessageRemoved(messageId)) return
        if (message.messageState != MessageState.COMMITTED) return

        val callback = CallbackBuilder(callbackClass)
            .override("onError") {
                Logger.xposedLog("Error deleting message $messageId")
            }.build()

        runCatching {
            updateMessageMethod.invoke(
                context.feature(Messaging::class).conversationManager,
                conversationId.instanceNonNull(),
                messageId,
                context.classCache.messageUpdateEnum.enumConstants.first { it.toString() == "ERASE" },
                callback
            )
        }.onFailure {
            Logger.xposedLog("Error deleting message $messageId", it)
        }

        //delay between deletes

        try {
            if(ConfigProperty.AUTO_PURGE_SPEED.toString() == "0")
            {
                Thread.sleep(1 * 1000) // default to 1 second
            }
            else{
                Thread.sleep(ConfigProperty.AUTO_PURGE_SPEED.toString().toLong() * 1000)
            }
        } catch (e: NumberFormatException) {
            println("The string is not a valid long integer.")
            Thread.sleep(1 * 1000) // default to 1 second
        }
    }

    private fun canDeleteMessage(message: Message): Boolean {
        if (message.senderId.toString() != myUserId) return false
        val contentType = message.messageContent.contentType.toString()

        return context.config.options(ConfigProperty.AUTO_PURGE_MESSAGES).filter { it.value }.any { it.key == contentType }
    }

    private fun canSave(): Boolean {
        if (context.config.options(ConfigProperty.AUTO_PURGE_MESSAGES).none { it.value }) return false

        with(context.feature(Messaging::class)) {
            if (openedConversationUUID == null) return@canSave false
            val conversation = openedConversationUUID.toString()
            if (context.feature(StealthMode::class).isStealth(conversation)) return@canSave false
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
                if (!canDeleteMessage(it)) return@forEach
                asyncSaveExecutorService.submit {
                    deleteMessage(conversationId, it)
                }
            }
        }
    }
}
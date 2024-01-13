package me.rhunk.snapenhance.core.features.impl.messaging

import me.rhunk.snapenhance.common.data.MessageState
import me.rhunk.snapenhance.common.data.MessageUpdate
import me.rhunk.snapenhance.common.data.MessagingRuleType
import me.rhunk.snapenhance.core.event.events.impl.ConversationUpdateEvent
import me.rhunk.snapenhance.core.features.FeatureLoadParams
import me.rhunk.snapenhance.core.features.MessagingRuleFeature
import me.rhunk.snapenhance.core.features.impl.spying.MessageLogger
import me.rhunk.snapenhance.core.features.impl.spying.StealthMode
import me.rhunk.snapenhance.core.util.hook.HookStage
import me.rhunk.snapenhance.core.util.hook.hook
import me.rhunk.snapenhance.core.util.ktx.getObjectField
import me.rhunk.snapenhance.core.wrapper.impl.Message
import me.rhunk.snapenhance.core.wrapper.impl.SnapUUID
import me.rhunk.snapenhance.mapper.impl.CallbackMapper
import java.util.concurrent.Executors

class AutoSave : MessagingRuleFeature("Auto Save", MessagingRuleType.AUTO_SAVE, loadParams = FeatureLoadParams.ACTIVITY_CREATE_ASYNC) {
    private val asyncSaveExecutorService = Executors.newSingleThreadExecutor()

    private val messageLogger by lazy { context.feature(MessageLogger::class) }

    private val autoSaveFilter by lazy {
        context.config.messaging.autoSaveMessagesInConversations.get()
    }

    fun saveMessage(conversationId: String, message: Message) {
        val messageId = message.messageDescriptor!!.messageId!!
        if (messageLogger.takeIf { it.isEnabled }?.isMessageDeleted(conversationId, messageId) == true) return

        runCatching {
            context.feature(Messaging::class).conversationManager?.updateMessage(
                conversationId,
                messageId,
                MessageUpdate.SAVE
            ) {
                if (it != null) {
                    context.log.warn("Error saving message $messageId: $it")
                }
            }
        }.onFailure {
            context.log.error("Error saving message $messageId", it)
        }

        //delay between saves
        Thread.sleep(100L)
    }

    fun canSaveMessage(message: Message, headless: Boolean = false): Boolean {
        if (message.messageState != MessageState.COMMITTED || message.messageMetadata?.isSaveable != true) return false

        if (!headless && (context.mainActivity == null || context.isMainActivityPaused)) return false
        if (message.messageMetadata!!.savedBy!!.any { uuid -> uuid.toString() == context.database.myUserId }) return false
        val contentType = message.messageContent!!.contentType.toString()

        return autoSaveFilter.any { it == contentType }
    }

    fun canSaveInConversation(targetConversationId: String, headless: Boolean = false): Boolean {
        val messaging = context.feature(Messaging::class)
        if (!headless) {
            if (messaging.openedConversationUUID?.toString() != targetConversationId) return false
        }

        if (context.feature(StealthMode::class).canUseRule(targetConversationId)) return false
        if (!canUseRule(targetConversationId)) return false

        return true
    }

    override fun asyncOnActivityCreate() {
        // called when enter in a conversation
        context.mappings.useMapper(CallbackMapper::class) {
            callbacks.getClass("FetchConversationWithMessagesCallback")?.hook(
                "onFetchConversationWithMessagesComplete",
                HookStage.BEFORE,
                { autoSaveFilter.isNotEmpty() }
            ) { param ->
                val conversationId = SnapUUID(param.arg<Any>(0).getObjectField("mConversationId")!!)
                if (!canSaveInConversation(conversationId.toString())) return@hook

                val messages = param.arg<List<Any>>(1).map { Message(it) }
                messages.forEach {
                    if (!canSaveMessage(it)) return@forEach
                    asyncSaveExecutorService.submit {
                        saveMessage(conversationId.toString(), it)
                    }
                }
            }
        }

        context.event.subscribe(
            ConversationUpdateEvent::class,
            { autoSaveFilter.isNotEmpty() }
        ) { event ->
            if (!canSaveInConversation(event.conversationId)) return@subscribe

            event.messages.forEach { message ->
                if (!canSaveMessage(message)) return@forEach
                asyncSaveExecutorService.submit {
                    saveMessage(event.conversationId, message)
                }
            }
        }
    }
}
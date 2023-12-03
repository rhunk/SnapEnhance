package me.rhunk.snapenhance.core.features.impl.tweaks

import android.view.View
import me.rhunk.snapenhance.core.event.events.impl.BindViewEvent
import me.rhunk.snapenhance.core.features.Feature
import me.rhunk.snapenhance.core.features.FeatureLoadParams
import me.rhunk.snapenhance.core.util.hook.HookStage
import me.rhunk.snapenhance.core.util.hook.hook
import me.rhunk.snapenhance.core.wrapper.impl.Message
import me.rhunk.snapenhance.core.wrapper.impl.SnapUUID

class PreventMessageListAutoScroll : Feature("PreventMessageListAutoScroll", loadParams = FeatureLoadParams.ACTIVITY_CREATE_SYNC) {
    private var openedConversationId: String? = null
    private val focusedMessages = mutableMapOf<View, Long>()
    private var firstFocusedMessageId: Long? = null
    private val delayedMessageUpdates = mutableListOf<() -> Unit>()

    override fun onActivityCreate() {
        if (!context.config.userInterface.preventMessageListAutoScroll.get()) return

        context.mappings.getMappedClass("callbacks", "ConversationManagerDelegate").hook("onConversationUpdated", HookStage.BEFORE) { param ->
            val updatedMessage = param.arg<ArrayList<*>>(2).map { Message(it) }.firstOrNull() ?: return@hook
            if (openedConversationId != updatedMessage.messageDescriptor?.conversationId.toString()) return@hook

            // cancel if the message is already in focus
            if (focusedMessages.entries.any { entry -> entry.value == updatedMessage.messageDescriptor?.messageId && entry.key.isAttachedToWindow }) return@hook

            val conversationLastMessages = context.database.getMessagesFromConversationId(
                openedConversationId.toString(),
                4
            ) ?: return@hook

            if (conversationLastMessages.none {
                focusedMessages.entries.any { entry -> entry.value == it.clientMessageId.toLong() && entry.key.isAttachedToWindow }
            }) {
                synchronized(delayedMessageUpdates) {
                    if (firstFocusedMessageId == null) firstFocusedMessageId = conversationLastMessages.lastOrNull()?.clientMessageId?.toLong()
                    delayedMessageUpdates.add {
                        param.invokeOriginal()
                    }
                }
                param.setResult(null)
            }
        }

        context.classCache.conversationManager.apply {
            hook("enterConversation", HookStage.BEFORE) { param ->
                openedConversationId = SnapUUID(param.arg(0)).toString()
            }
            hook("exitConversation", HookStage.BEFORE) {
                openedConversationId = null
                firstFocusedMessageId = null
                synchronized(focusedMessages) {
                    focusedMessages.clear()
                }
                synchronized(delayedMessageUpdates) {
                    delayedMessageUpdates.clear()
                }
            }
        }

        context.event.subscribe(BindViewEvent::class) { event ->
            event.chatMessage { conversationId, messageId ->
                if (conversationId != openedConversationId) return@chatMessage
                synchronized(focusedMessages) {
                    focusedMessages[event.view] = messageId.toLong()
                }

                if (delayedMessageUpdates.isNotEmpty() && focusedMessages.entries.any { entry -> entry.value == firstFocusedMessageId && entry.key.isAttachedToWindow }) {
                    delayedMessageUpdates.apply {
                        synchronized(this) {
                            removeIf { it(); true }
                            firstFocusedMessageId = null
                        }
                    }
                }
            }
        }
    }
}